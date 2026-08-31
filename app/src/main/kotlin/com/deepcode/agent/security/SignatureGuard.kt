package com.deepcode.agent.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * 应用完整性校验（防二次打包）。
 *
 * ## 它能防什么
 *
 * v2/v3 签名已经保证了「APK 内容被篡改就无法安装」——但这只挡住改内容，
 * 挡不住**整包重签名**：攻击者解包 → 植入恶意代码 → 用自己的密钥重签 →
 * 诱导用户安装。此时签名依然有效，系统不报错，但签名者已经换人了。
 *
 * 本类比对「实际签名证书的 SHA-256」与「官方发布密钥的 SHA-256」来识别这种情况：
 * 重签名必然换证书，指纹就一定对不上。
 *
 * ## 它的局限（别高估）
 *
 * 攻击者若能反编译改 smali、把比对逻辑本身绕过，这个检查就失效了。
 * 所以它是**提高攻击成本**，不是绝对防线。真正的防线是
 * v2/v3 签名 + R8 混淆（见 proguard-rules.pro）+ 不从第三方渠道装包。
 *
 * ## 两条必须知道的跳过规则
 *
 * 1. **来自 Play 商店的安装直接跳过**。
 *    Google Play 的 App Signing 会用**它自己的密钥**给 APK 重新签名，
 *    证书指纹随之变成 Play 的指纹，跟我们的上传密钥不一致。
 *    不跳过的话，所有从 Play 安装的用户都会被误判成盗版并崩溃。
 *    而从 Play 安装本身就由 Play 背书了来源，不必再自己验一遍。
 *
 * 2. **Android debug 证书直接跳过**。
 *    本地跑 assembleRelease 但没配 keystore 时，构建会回退到 debug 签名。
 *    这种包指纹当然对不上——但那是开发者的正常操作，不该直接崩。
 *    debug 证书的 DN 恒为 `CN=Android Debug,O=Android,C=US`，据此识别。
 *
 * ## 维护
 *
 * 重新生成签名密钥、或启用了 Play App Signing 之后，
 * 必须同步更新 [OFFICIAL_SIGNATURE_SHA256]（或在 Play 场景依赖安装来源跳过），
 * 否则正式包会全部校验失败。指纹获取方式见 RELEASING.md。
 */
object SignatureGuard {

    private const val TAG = "SignatureGuard"

    /**
     * 官方发布密钥的证书 SHA-256（小写、无冒号）。
     *
     * 这不是机密——它本来就以明文形式存在于 APK 的签名块里，
     * 任何人都能读到。写在这里只是作为比对基准。
     */
    private const val OFFICIAL_SIGNATURE_SHA256 =
        "062e803e2e8f7914861023cb2b9e93dc1cb6dc521a51331a028880b6e621c6b4"

    /** Android SDK 自动生成的 debug 证书，DN 是固定的 */
    private const val DEBUG_CERT_DN_MARKER = "Android Debug"

    private const val PLAY_STORE_INSTALLER = "com.android.vending"

    /** 校验结果 */
    sealed interface Result {
        /** 指纹匹配 */
        data object Trusted : Result

        /** 来自 Google Play，信任渠道背书，跳过指纹比对 */
        data object TrustedByInstaller : Result

        /** 用的是 Android debug 证书，属于开发构建，跳过 */
        data object TrustedDebugCertificate : Result

        /** 指纹不匹配：几乎可以确定是被重签名过的包 */
        data class Tampered(val actual: String, val expected: String) : Result

        /** 读不到签名信息（极少数 ROM 的兼容性问题），不应当因此阻断用户 */
        data class Unknown(val cause: Throwable) : Result
    }

    /**
     * 校验自身签名。
     *
     * 只做检测不做处置——处置策略交给调用方，
     * 这样便于单独测试，也不必在测试里真的把进程杀掉。
     */
    fun verify(context: Context): Result {
        // 先查来源并放在最前面：绝大多数正常用户走这一条就返回了，
        // 省掉解析证书的开销。
        val installer = runCatching {
            context.packageManager.getInstallerPackageName(context.packageName)
        }.getOrNull()

        if (installer == PLAY_STORE_INSTALLER) {
            Log.i(TAG, "来源为 Google Play，跳过签名指纹校验")
            return Result.TrustedByInstaller
        }

        return try {
            val certs = signerCertificates(context)
            if (certs.isEmpty()) {
                return Result.Unknown(IllegalStateException("签名证书列表为空"))
            }

            // 开发构建：debug 证书直接放行，避免本地 release 构建启动即崩
            if (certs.any { DEBUG_CERT_DN_MARKER in it.subjectX500Principal.name }) {
                Log.i(TAG, "检测到 Android debug 证书，按开发构建处理")
                return Result.TrustedDebugCertificate
            }

            val actual = sha256(certs.first().encoded)

            if (actual.equals(OFFICIAL_SIGNATURE_SHA256, ignoreCase = true)) {
                Log.i(TAG, "签名校验通过: $actual")
                Result.Trusted
            } else {
                Log.w(TAG, "签名不匹配！实际=$actual 期望=$OFFICIAL_SIGNATURE_SHA256")
                Result.Tampered(actual = actual, expected = OFFICIAL_SIGNATURE_SHA256)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "无法读取签名信息", t)
            Result.Unknown(t)
        }
    }

    /**
     * 取出当前 APK 的签名证书。
     *
     * API 28 之前只能用已废弃的 GET_SIGNATURES；
     * API 28+ 用 GET_SIGNING_CERTIFICATES。注意后者在启用 v3 密钥轮换时会
     * 一并返回历史签名者，所以取 apkContentsSigners（当前实际签名者），
     * 而不是 signingCertificateHistory（含已轮换掉的旧证书）。
     */
    @Suppress("DEPRECATION")
    private fun signerCertificates(context: Context): List<X509Certificate> {
        val pm = context.packageManager
        val packageName = context.packageName

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
                ?: throw IllegalStateException("PackageInfo.signingInfo 为 null")
            signingInfo.apkContentsSigners
        } else {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            info.signatures
        } ?: throw IllegalStateException("取不到签名数组")

        val factory = CertificateFactory.getInstance("X.509")
        return signatures.mapNotNull { sig ->
            runCatching {
                factory.generateCertificate(
                    ByteArrayInputStream(sig.toByteArray()),
                ) as? X509Certificate
            }.getOrNull()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
