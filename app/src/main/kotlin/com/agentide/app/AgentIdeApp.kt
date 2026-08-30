package com.agentide.app

import android.app.Application
import com.agentide.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AgentIdeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AgentIdeApp)
            modules(appModule)
        }
    }
}
