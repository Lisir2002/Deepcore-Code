package com.deepcode.feature.settings

import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

/** 设置页的 Koin 装配（仅 ViewModel）。配置与连接由 :app 的 appModule 提供。 */
val settingsModule = module {
    viewModelOf(::SettingsViewModel)
    viewModelOf(::SettingsModelViewModel)
    viewModelOf(::ProviderEditViewModel)
}
