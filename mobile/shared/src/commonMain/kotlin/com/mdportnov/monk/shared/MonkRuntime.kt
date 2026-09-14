package com.mdportnov.monk.shared

import com.mdportnov.monk.shared.data.KeyValueStore
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.platform.MonkPlatform

/** Process-wide graph. Initialised once by the platform entry point (Application / MainViewController). */
object MonkRuntime {
    lateinit var store: MonkStore
        private set
    lateinit var platform: MonkPlatform
        private set

    val isInitialized get() = ::store.isInitialized

    fun init(kv: KeyValueStore, platform: MonkPlatform) {
        if (isInitialized) return
        store = MonkStore(kv)
        this.platform = platform
    }
}
