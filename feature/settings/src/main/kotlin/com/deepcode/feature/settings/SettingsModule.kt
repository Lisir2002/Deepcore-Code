package com.deepcode.feature.settings

import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

/**
 * 设置页的 Koin 装配（ViewModel + 流程单例）。
 * 配置与连接（ModelConfigStore / ModelProviderRegistry）由 :app 的 appModule 提供。
 */
val settingsModule = module {

    // 端点草稿跨 Step1/Step2 共享的单例载具（见 ProviderEditFlow 注释）。
    single { ProviderEditFlow() }

    viewModelOf(::SettingsViewModel)
    viewModelOf(::SettingsModelViewModel)
    viewModelOf(::ProviderEditViewModel)
}