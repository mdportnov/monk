package com.mdportnov.monk

import android.app.Application
import android.content.Context
import com.mdportnov.monk.shared.MonkRuntime
import com.mdportnov.monk.shared.platform.SharedPrefsStore
import com.mdportnov.monk.update.GitHubUpdater

class MonkApplication : Application() {
    lateinit var updater: GitHubUpdater
        private set

    override fun onCreate() {
        super.onCreate()
        updater = GitHubUpdater(this)
        MonkRuntime.init(SharedPrefsStore(this), AndroidPlatform(this, updater))
    }

    companion object {
        fun updater(context: Context): GitHubUpdater? = (context.applicationContext as? MonkApplication)?.updater
    }
}
