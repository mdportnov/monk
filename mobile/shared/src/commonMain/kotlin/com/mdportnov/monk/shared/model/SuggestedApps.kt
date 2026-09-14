package com.mdportnov.monk.shared.model

/**
 * Well-known attention sinks, matched by exact package id (with the Lite / regional variants
 * each vendor ships). Only the ones actually installed are ever shown.
 */
object SuggestedApps {
    data class Suggestion(val name: String, val packages: List<String>)

    val catalog: List<Suggestion> = listOf(
        Suggestion("Instagram", listOf("com.instagram.android", "com.instagram.lite")),
        Suggestion("YouTube", listOf("com.google.android.youtube")),
        Suggestion("TikTok", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go")),
        Suggestion("Threads", listOf("com.instagram.barcelona")),
        Suggestion("Facebook", listOf("com.facebook.katana", "com.facebook.lite")),
        Suggestion("X", listOf("com.twitter.android")),
        Suggestion("Reddit", listOf("com.reddit.frontpage")),
        Suggestion("Snapchat", listOf("com.snapchat.android")),
        Suggestion("Telegram", listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")),
        Suggestion("WhatsApp", listOf("com.whatsapp")),
        Suggestion("VK", listOf("com.vkontakte.android")),
        Suggestion("Pinterest", listOf("com.pinterest")),
        Suggestion("Twitch", listOf("tv.twitch.android.app")),
        Suggestion("Netflix", listOf("com.netflix.mediaclient")),
        Suggestion("YouTube Shorts / Music", listOf("com.google.android.apps.youtube.music")),
    )

    private val byPackage: Map<String, Suggestion> = catalog.flatMap { s -> s.packages.map { it to s } }.toMap()

    fun isSuggested(packageName: String) = packageName in byPackage

    /** Installed apps that are on the list, in catalog order. */
    fun pick(installed: List<InstalledApp>): List<InstalledApp> {
        val have = installed.associateBy { it.packageName }
        return catalog.flatMap { s -> s.packages.mapNotNull { have[it] } }
    }
}
