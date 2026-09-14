package com.mdportnov.monk

import android.app.Application
import com.mdportnov.monk.shared.MonkRuntime
import com.mdportnov.monk.shared.platform.SharedPrefsStore

class MonkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MonkRuntime.init(SharedPrefsStore(this), AndroidPlatform(this))
    }
}
