package com.deepcode.agent.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.deepcode.core.logging.Log
import com.deepcode.core.logging.LogCategory
import com.deepcode.core.logging.LogLevel
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 加密器（Android Keystore AES-256-GCM，借鉴 deepcode-R 的 CredentialEncryptor）。
 *
 * 把用户配置的模型 API Key 加密后再落盘，避免明文存入 SharedPreferences/备份文件。
 * 架构：AndroidKeyStore 生成一枚不可导出的 256-bit AES 密钥（alias=[ALIAS]），
 * encrypt 用 AES/GCM/NoPadding 加密，密文串形式 `V1:<Base64(IV + ciphertext + GCM tag)>`；
 * decrypt 按前缀解算，读到非常规格式（如旧明文 / 其它密钥加密的历史值）失败回退空串，
 * 让 UI 显示为"未填写"，不崩客户端。
 *
 * 说明：本实现为单密钥直接加密（无 DEK 包装层），因为模型 API Key 仅一行小字符串，
 * 无大批量凭据轮换诉求，Keystore 单密钥 + 幂等初始化即可，够满足存储加密诉求。
 * 同步调用（SharedPreferences 存储为同步 API），内部 runCatching 兜底。
 */
object KeyEncryptor {

    /** V1 前缀：版本化，方便后续升级密钥轮换方案。 */
    private const val SCHEME_V1 = "V1:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12
    private const val ALIAS = "deepcore_llm_api_key"

    private val keyStore: KeyStore by lazy { loadKeyStore() }

    private fun loadKeyStore(): KeyStore {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        // 首次调用即生成密钥（幂等）。
        if (!ks.containsAlias(ALIAS)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setKeySize(256)
                    .build(),
            )
            generator.generateKey()
        }
        return ks
    }

    /** 加密明文；返回 `V1:<Base64(IV + ciphertext + tag)>`；空串原样返回。失败抛 [IllegalStateException]。 */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return plain
        return runCatching {
            val key = secretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            SCHEME_V1 + Base64.getEncoder().encodeToString(cipher.iv + ct)
        }.getOrElse { e ->
            logWarn("encrypt", e)
            throw IllegalStateException("API Key 加密失败: ${e.message}", e)
        }
    }

    /** 解密 `V1:` 密文；空串 / 非 V1 格式 / 失败一律返回原值或空（不崩）。 */
    fun decrypt(formatted: String): String {
        if (formatted.isEmpty()) return formatted
        if (!formatted.startsWith(SCHEME_V1)) {
            // 非 V1 前缀：视为对外暴露的明文历史值？为安全起见返回空串，提示重新填写。
            // （迁移路径：store 在写入前会把明文换成密文，这里兜底返回空避免把明文当解密结果。）
            return ""
        }
        return runCatching {
            val combined = Base64.getDecoder().decode(formatted.removePrefix(SCHEME_V1))
            if (combined.size < IV_LEN + 1) return ""
            val iv = combined.copyOfRange(0, IV_LEN)
            val ct = combined.copyOfRange(IV_LEN, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrElse { e ->
            logWarn("decrypt", e)
            ""
        }
    }

    private fun secretKey(): SecretKey {
        // 每次从 Keystore 取密钥（object 无跨调用状态），弱引用语义由系统管理。
        val entry = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Keystore 密钥缺失（ALIAS=$ALIAS）")
        return entry.secretKey
    }

    private fun logWarn(op: String, e: Throwable) {
        Log.log(
            LogLevel.WARN, LogCategory.SECURITY_CREDENTIAL, "KeyEncryptor",
            "API Key $op 失败：${e.message}",
        )
    }
}