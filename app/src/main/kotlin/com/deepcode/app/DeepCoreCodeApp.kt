package com.deepcode.app

import android.app.Application
import com.deepcode.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DeepCoreCodeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DeepCoreCodeApp)
            modules(appModule)
        }
    }
}
