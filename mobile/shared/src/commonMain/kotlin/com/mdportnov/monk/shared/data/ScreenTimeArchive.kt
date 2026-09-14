package com.mdportnov.monk.shared.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Monk's own copy of the OS screen-time log. Android keeps usage events for about ten days;
 * every time a report is computed the days the OS still covers are written here, so a day
 * outlives the log once Monk has seen it. Days Monk never saw (the app not opened for two
 * weeks) are gone for good — the archive is a mirror, not a recorder.
 *
 * One JSON blob in its own prefs file: date → package → foreground millis, the top
 * [MAX_APPS_PER_DAY] apps of each day, at most [MAX_DAYS] days. All callers on the main thread.
 */
class ScreenTimeArchive(private val kv: KeyValueStore) {
    @Serializable
    private data class Blob(val days: Map<String, Map<String, Long>> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** date → package → millis. */
    var days: Map<String, Map<String, Long>> = load()
        private set

    val earliestDate: String? get() = days.keys.minOrNull()

    /**
     * Writes [osByApp] for every day in [covered] (today included: it is provisional and gets
     * replaced by the next write while the OS still has it). Returns whether anything changed.
     */
    fun record(osByApp: Map<String, Map<String, Long>>, covered: Set<String>): Boolean {
        if (covered.isEmpty()) return false
        val next = HashMap(days)
        for (d in covered) {
            val perApp = osByApp.mapNotNull { (pkg, perDay) -> perDay[d]?.takeIf { it > 0 }?.let { pkg to it } }
                .sortedByDescending { it.second }
                .take(MAX_APPS_PER_DAY)
                .toMap()
            if (perApp.isEmpty()) next.remove(d) else next[d] = perApp
        }
        val trimmed = next.keys.sortedDescending().take(MAX_DAYS).associateWith { next.getValue(it) }
        if (trimmed == days) return false
        days = trimmed
        kv.putString(KEY, json.encodeToString(Blob.serializer(), Blob(trimmed)))
        return true
    }

    fun clear() {
        days = emptyMap()
        kv.remove(KEY)
    }

    private fun load(): Map<String, Map<String, Long>> {
        val raw = kv.getString(KEY) ?: return emptyMap()
        return runCatching { json.decodeFromString(Blob.serializer(), raw).days }.getOrDefault(emptyMap())
    }

    companion object {
        const val MAX_DAYS = 365
        const val MAX_APPS_PER_DAY = 50
        private const val KEY = "days"
    }
}
