package com.mdportnov.monk

import android.app.Application
import android.content.Context
import android.util.Log
import com.mdportnov.monk.shared.MonkGraph
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.platform.SharedPrefsStore
import com.mdportnov.monk.update.GitHubUpdater
import com.mdportnov.monk.tiles.TileRegistry

/** Android object graph: everything the shared graph has, plus what only Android needs. */
class AndroidGraph(
    val shared: MonkGraph,
    val platform: AndroidPlatform,
    val updater: GitHubUpdater,
    val intercepts: InterceptRegistry,
) {
    val store get() = shared.store
}

class MonkApplication : Application() {
    lateinit var graph: AndroidGraph
        private set

    override fun onCreate() {
        super.onCreate()
        val store = MonkStore(
            SharedPrefsStore(this),
            onLoadFailure = { key, e -> Log.e("Monk", "could not read $key; kept as $key.bak", e) },
            statsKv = SharedPrefsStore(this, "monk_stats"),
        )
        val updater = GitHubUpdater(this)
        TileRegistry.load(this)
        val platform = AndroidPlatform(this, updater)
        graph = AndroidGraph(MonkGraph(store, platform), platform, updater, InterceptRegistry(store))
    }
}

/** The one place Android components resolve their dependencies. */
val Context.monkGraph: AndroidGraph
    get() = (applicationContext as MonkApplication).graph
