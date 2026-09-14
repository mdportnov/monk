package com.mdportnov.monk.shared.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.mdportnov.monk.shared.platform.systemLanguage

class Strings(private val ru: Boolean) {
    private fun t(en: String, ruText: String) = if (ru) ruText else en

    val appName get() = "Monk"
    val tabHome get() = t("Apps", "Приложения")
    val tabStats get() = t("Stats", "Статистика")
    val tabSettings get() = t("Settings", "Настройки")

    val protection get() = t("Protection", "Защита")
    val protectionOn get() = t("On", "Включена")
    val protectionOff get() = t("Off", "Выключена")
    val protectionPaused get() = t("Paused by schedule", "Пауза по расписанию")
    val setupTitle get() = t("Finish setup", "Завершите настройку")
    val setupAccessibility get() = t("Accessibility service", "Служба специальных возможностей")
    val setupAccessibilityHint get() = t(
        "Monk needs it to notice which app is in front. It never reads screen content.",
        "Нужна, чтобы Monk видел, какое приложение открыто. Содержимое экрана не читается.",
    )
    val setupRestricted get() = t(
        "Android 13+: the switch is greyed out for sideloaded apps. Tap it once anyway, then open App info → ⋮ → Allow restricted settings, and come back.",
        "Android 13+: для установленных вручную приложений переключатель серый. Всё равно нажмите его один раз, затем откройте О приложении → ⋮ → Разрешить ограниченные настройки и вернитесь.",
    )
    val enable get() = t("Enable", "Включить")
    val grant get() = t("Grant", "Разрешить")
    val appInfo get() = t("App info", "О приложении")
    val granted get() = t("Granted", "Есть")
    val enabled get() = t("Enabled", "Включена")

    val blockedApps get() = t("Watched apps", "Под контролем")
    val noApps get() = t("No apps yet", "Пока пусто")
    val noAppsHint get() = t(
        "Add the apps that steal your attention. Each one gets a pause or a hard block.",
        "Добавьте приложения, которые крадут внимание. Каждому — пауза или полный запрет.",
    )
    val addApps get() = t("Add apps", "Добавить")
    val search get() = t("Search", "Поиск")
    val done get() = t("Done", "Готово")
    val cancel get() = t("Cancel", "Отмена")
    val remove get() = t("Remove", "Убрать")
    val save get() = t("Save", "Сохранить")
    val selected get() = t("selected", "выбрано")
    val loadingApps get() = t("Reading installed apps…", "Читаю список приложений…")

    val modeTitle get() = t("Mode", "Режим")
    val modeBlock get() = t("Block", "Запрет")
    val modeBlockHint get() = t("Never opens while protection is on.", "Не открывается, пока включена защита.")
    val modeDelay get() = t("Pause", "Пауза")
    val modeDelayHint get() = t("Breathe first, then decide.", "Сначала вдох-выдох, потом решение.")
    val delayLength get() = t("Pause length", "Длина паузы")
    val allowLength get() = t("Open for", "Открывать на")
    val useDefault get() = t("Use default", "По умолчанию")
    val seconds get() = t("s", "с")
    val minutes get() = t("min", "мин")
    fun pauseChip(seconds: Int) = t("${seconds}s pause", "Пауза ${seconds}с")
    fun openFor(minutes: Int) = t("Open for $minutes min", "Открыть на $minutes мин")

    val defaults get() = t("Defaults", "По умолчанию")
    val defaultsHint get() = t("Used by apps without their own setting.", "Для приложений без своих настроек.")
    val scheduleTitle get() = t("Schedule", "Расписание")
    val scheduleHint get() = t("Protect only inside this window. Off = always.", "Защита только в этом окне. Выкл = всегда.")
    val scheduleAllDay get() = t("Same start and end = the whole day.", "Одинаковые начало и конец = весь день.")
    val from get() = t("From", "С")
    val to get() = t("To", "До")
    val about get() = t("About", "О приложении")
    val aboutText get() = t(
        "Monk puts a moment of friction between you and the apps you open on autopilot. Everything stays on the device.",
        "Monk ставит секунду трения между вами и приложениями, которые открываются на автопилоте. Всё остаётся на телефоне.",
    )
    val language get() = t("Language follows the system", "Язык берётся из системы")
    val dayShort get() = if (ru) listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс") else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    val statsToday get() = t("Today", "Сегодня")
    val statsAllTime get() = t("All time", "За всё время")
    val intercepted get() = t("Paused", "Остановок")
    val turnedAway get() = t("Walked away", "Ушли")
    val opened get() = t("Opened anyway", "Открыли")
    val statsEmpty get() = t("Nothing intercepted yet.", "Пока ни одной остановки.")
    val last14 get() = t("Last 14 days", "Последние 14 дней")

    val interceptBreathe get() = t("Breathe", "Вдох. Выдох.")
    val interceptQuestion get() = t("Do you really want to open", "Правда хотите открыть")
    fun interceptBlockedTitle(label: String) = t("$label is blocked", "$label под запретом")
    fun interceptQuestionApp(label: String) = t("$label?", "$label?")
    val interceptBlockedHint get() = t("You chose this earlier. Future you says thanks.", "Вы сами так решили. Будущий вы скажет спасибо.")
    val interceptNotNow get() = t("Not now", "Не сейчас")
    val interceptBack get() = t("Back to focus", "Вернуться к делу")
    fun interceptWait(seconds: Int) = t("Wait ${seconds}s", "Подождите ${seconds}с")

    val iosTitle get() = t("iOS is not supported yet", "iOS пока не поддерживается")
    val iosBody get() = t(
        "Apple keeps app interception behind the Screen Time API. This build only ships the Android blocker; the iOS shell is a placeholder.",
        "Apple прячет перехват приложений за Screen Time API. В этой сборке блокировщик только для Android, оболочка iOS — заглушка.",
    )
}

val LocalStrings = staticCompositionLocalOf { Strings(ru = false) }

fun stringsForSystem() = Strings(ru = systemLanguage().lowercase().startsWith("ru"))

val strings: Strings
    @Composable get() = LocalStrings.current
