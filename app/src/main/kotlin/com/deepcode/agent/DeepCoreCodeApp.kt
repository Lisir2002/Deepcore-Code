package com.deepcode.agent

import android.app.Application
import com.deepcode.agent.di.appModule
import com.deepcode.agent.security.SignatureGuard
import com.deepcode.feature.settings.settingsModule
import com.deepcode.core.mcp.McpServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named

class DeepCoreCodeApp : Application(), KoinComponent {

    override fun onCreate() {
        super.onCreate()
        checkIntegrity()
        startKoin {
            androidContext(this@DeepCoreCodeApp)
            modules(appModule, settingsModule)
        }
        // 启动后连接已配置的 MCP server；单点失败只记日志，不阻断 App（见 McpServerManager.connectAll）。
        val agentScope: CoroutineScope by inject(named("agent"))
        agentScope.launch {
            runCatching { get<McpServerManager>().connectAll() }
                .onFailure { android.util.Log.w("DeepCoreCodeApp", "MCP 连接初始化失败：${it.message}") }
        }
    }

    /**
     * 启动时的完整性校验。
     *
     * 处置策略刻意分了两档：
     *   · debug 构建只记录不阻断——开发者天天用 debug 签名，拦了纯属自找麻烦；
     *   · release 构建遇到 [SignatureGuard.Result.Tampered] 直接抛异常终止启动。
     *
     * 为什么 release 敢直接抛：走到 Tampered 说明签名证书不是我们的发布密钥，
     * 也不是 debug 证书，也不来自 Play——正常分发路径下不可能出现这个组合。
     * 这种包里的 API Key 和对话内容都不可信，让它继续跑比让它崩更糟。
     *
     * 注意 [SignatureGuard.Result.Unknown] 是放行的：
     * 读不到签名通常是 ROM 兼容性问题，为这个把正常用户挡在门外不值得。
     */
    private fun checkIntegrity() {
        when (val result = SignatureGuard.verify(this)) {
            is SignatureGuard.Result.Trusted,
            is SignatureGuard.Result.TrustedByInstaller,
            is SignatureGuard.Result.TrustedDebugCertificate,
            is SignatureGuard.Result.Unknown,
                -> Unit

            is SignatureGuard.Result.Tampered -> {
                if (BuildConfig.DEBUG) {
                    // debug 阶段只看不拦，但要留痕，免得真出问题时毫无头绪
                    android.util.Log.w(
                        "DeepCoreCodeApp",
                        "检测到非官方签名（debug 构建，不阻断）：" +
                            "实际=${result.actual} 期望=${result.expected}",
                    )
                } else {
                    throw SecurityException(
                        "应用签名校验失败，可能已被重新打包。" +
                            "请从官方渠道安装 DeepCore-Code。",
                    )
                }
            }
        }
    }
}
