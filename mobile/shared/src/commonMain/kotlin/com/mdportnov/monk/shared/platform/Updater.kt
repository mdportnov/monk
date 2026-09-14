package com.mdportnov.monk.shared.platform

import kotlinx.coroutines.flow.StateFlow

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String, val notes: String, val sizeBytes: Long) : UpdateState
    data class Downloading(val version: String, val progress: Float) : UpdateState
    data class Verifying(val version: String) : UpdateState
    data class Installing(val version: String) : UpdateState
    /** The OS refuses installs from Monk until the user allows "unknown apps" for it. */
    data class NeedsInstallPermission(val version: String) : UpdateState
    data class Failed(val version: String?, val reason: String) : UpdateState
}

/** Self-update from GitHub Releases. null on platforms that ship through a store. */
interface Updater {
    val currentVersion: String
    val state: StateFlow<UpdateState>
    fun check(force: Boolean)
    /** Downloads, verifies and hands the APK to the installer; also the retry after a failure. */
    fun install()
    fun openInstallPermission()
}
