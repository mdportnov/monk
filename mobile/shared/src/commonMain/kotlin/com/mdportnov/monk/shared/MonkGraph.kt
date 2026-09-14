package com.mdportnov.monk.shared

import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.platform.MonkPlatform

/**
 * The object graph. Built once by the platform entry point and handed down explicitly: the
 * shared UI never reaches for a global.
 */
class MonkGraph(
    val store: MonkStore,
    val platform: MonkPlatform,
)
