package com.mdportnov.monk.shared.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.mdportnov.monk.shared.model.Intention
import com.mdportnov.monk.shared.platform.systemLanguage

class Strings(private val ru: Boolean) {
    private fun t(en: String, ruText: String) = if (ru) ruText else en

    val appName get() = "Monk"
    val tabHome get() = t("Apps", "Приложения")
    val tabStats get() = t("Stats", "Статистика")
    val tabSettings get() = t("Settings", "Настройки")

    // Status
    val protection get() = t("Protection", "Защита")
    val protectionOn get() = t("On", "Включена")
    val protectionOff get() = t("Off", "Выключена")
    val protectionPaused get() = t("Paused by schedule", "Пауза по расписанию")
    fun pausedUntil(time: String) = t("Paused until $time", "Пауза до $time")
    fun strictUntil(time: String) = t("Strict mode until $time", "Строгий режим до $time")
    val pauseFor get() = t("Pause", "Пауза")
    val pause15 get() = t("15 min", "15 мин")
    val pause60 get() = t("1 hour", "1 час")
    val pauseDay get() = t("Today", "До завтра")
    val resume get() = t("Resume", "Продолжить")
    val focus get() = t("Focus", "Фокус")
    val focus25 get() = t("25 min", "25 мин")
    val focus50 get() = t("50 min", "50 мин")
    fun focusUntil(time: String) = t("Focus until $time", "Фокус до $time")
    val focusHint get() = t("Every watched app is blocked until the timer ends. No way to stop early.", "Все приложения под контролем закрыты до конца таймера. Остановить раньше нельзя.")
    val focusConfirmTitle get() = t("Start a focus session?", "Начать фокус-сессию?")
    fun focusConfirmBody(time: String) = t("All watched apps stay blocked until $time. No way to stop early.", "Все приложения под контролем закрыты до $time. Остановить раньше нельзя.")
    val start get() = t("Start", "Начать")
    val todayForApp get() = t("Today", "Сегодня")
    val noData get() = t("No data for this period.", "За этот период данных нет.")
    fun pauses(n: Int) = t(if (n == 1) "1 pause" else "$n pauses", "$n ${plural(n, "остановка", "остановки", "остановок")}")
    val strictLocked get() = t("Locked by strict mode", "Заблокировано строгим режимом")

    // Setup
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
    val appInfo get() = t("App info", "О приложении")
    val enabled get() = t("Enabled", "Включена")

    // Week summary
    val thisWeek get() = t("This week", "Эта неделя")
    fun weekLine(paused: Int, away: Int) = t("${pauses(paused)} · $away walked away", "${pauses(paused)} · $away раз ушли")
    fun successRate(pct: Int) = t("$pct% walked away", "$pct% ушли")
    fun streak(days: Int) = t(if (days == 1) "1-day streak" else "$days-day streak", "серия: $days ${plural(days, "день", "дня", "дней")}")
    val noWeekData get() = t("No pauses yet this week.", "На этой неделе остановок ещё не было.")

    // Apps
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
    val loadingApps get() = t("Reading installed apps…", "Читаю список приложений…")
    fun openUntil(time: String) = t("Open until $time", "Открыто до $time")
    val endNow get() = t("End now", "Закрыть")
    fun limitToday(used: Int, limit: Int) = t("$used/$limit today", "$used/$limit сегодня")

    // App detail
    val modeTitle get() = t("Mode", "Режим")
    val modeBlock get() = t("Block", "Запрет")
    val modeBlockHint get() = t("Never opens while protection is on.", "Не открывается, пока включена защита.")
    val modeDelay get() = t("Pause", "Пауза")
    val modeDelayHint get() = t("Breathe first, then decide.", "Сначала вдох-выдох, потом решение.")
    val delayLength get() = t("Pause length", "Длина паузы")
    val allowLength get() = t("Open for", "Открывать на")
    val dailyLimit get() = t("Daily limit", "Лимит в день")
    val dailyLimitHint get() = t("After this many opens the app is blocked until midnight.", "После стольких открытий приложение закрыто до полуночи.")
    val noLimit get() = t("No limit", "Без лимита")
    val useDefault get() = t("Use default", "По умолчанию")
    val seconds get() = t("s", "с")
    val minutes get() = t("min", "мин")
    val times get() = t("×", "×")
    fun pauseChip(seconds: Int) = t("${seconds}s pause", "Пауза ${seconds}с")
    fun openFor(minutes: Int) = t("Open for $minutes min", "Открыть на $minutes мин")

    // Settings
    val defaults get() = t("Defaults", "По умолчанию")
    val defaultsHint get() = t("Used by apps without their own setting.", "Для приложений без своих настроек.")
    val scheduleTitle get() = t("Schedule", "Расписание")
    val scheduleHint get() = t("Protect only inside this window. Off = always.", "Защита только в этом окне. Выкл = всегда.")
    val scheduleAllDay get() = t("Same start and end = the whole day.", "Одинаковые начало и конец = весь день.")
    val from get() = t("From", "С")
    val to get() = t("To", "До")
    val pauseScreen get() = t("Pause screen", "Экран паузы")
    val askIntention get() = t("Ask why", "Спрашивать зачем")
    val askIntentionHint get() = t("Before opening, pick a reason. Shows up in stats.", "Перед открытием выбрать причину. Видно в статистике.")
    val pauseMessage get() = t("Your line", "Ваша фраза")
    val pauseMessageHint get() = t("Shown on the pause screen instead of the default. Leave empty for the default.", "Показывается на экране паузы вместо стандартной. Пусто = стандартная.")
    val reliability get() = t("Reliability", "Надёжность")
    val overlayMode get() = t("Overlay mode", "Режим оверлея")
    val overlayModeHint get() = t(
        "Draw the pause screen on top of everything. Turn on if the pause screen does not appear on your phone, or you use split-screen.",
        "Рисовать экран паузы поверх всего. Включите, если экран паузы не появляется на вашем телефоне или вы пользуетесь разделённым экраном.",
    )
    val notifyWhenOff get() = t("Warn when protection stops", "Предупреждать, если защита выключилась")
    val notifyWhenOffHint get() = t("A notification if the system turns the accessibility service off.", "Уведомление, если система выключила службу специальных возможностей.")
    val notifyPermission get() = t("Allow notifications", "Разрешить уведомления")
    val quickTiles get() = t("Quick Settings tiles", "Плитки в шторке")
    val quickTilesHint get() = t("Add \"Monk pause\" and \"Monk focus\" tiles to the notification shade for one-tap control.", "Добавьте плитки «Monk: пауза» и «Monk: фокус» в шторку для управления одним касанием.")
    val addTiles get() = t("Add tiles", "Добавить плитки")
    val tilePause get() = t("Monk pause", "Monk: пауза")
    val tileFocus get() = t("Monk focus", "Monk: фокус")
    val serviceOffTitle get() = t("Monk protection stopped", "Защита Monk выключилась")
    val serviceOffBody get() = t("The accessibility service is off, so watched apps open freely. Tap to turn it back on.", "Служба специальных возможностей выключена, приложения открываются свободно. Нажмите, чтобы включить обратно.")
    val strictTitle get() = t("Strict mode", "Строгий режим")
    val strictHint get() = t(
        "Until the chosen time you cannot turn protection off, pause it, remove apps or soften their settings. No way back.",
        "До выбранного времени нельзя выключить защиту, поставить паузу, убрать приложения или ослабить настройки. Обратного пути нет.",
    )
    val strictUntilMidnight get() = t("Until midnight", "До полуночи")
    val strict3h get() = t("For 3 hours", "На 3 часа")
    val strictConfirmTitle get() = t("Enable strict mode?", "Включить строгий режим?")
    fun strictConfirmBody(time: String) = t("Nothing can be softened until $time. Not even by you.", "До $time ничего нельзя ослабить. Даже вам.")
    val confirm get() = t("Enable", "Включить")
    val appearance get() = t("Appearance", "Оформление")
    val themeSystem get() = t("System", "Как в системе")
    val themeLight get() = t("Light", "Светлая")
    val themeDark get() = t("Dark", "Тёмная")
    val dynamicColor get() = t("Wallpaper colors", "Цвета обоев")
    val dynamicColorHint get() = t("Material You palette from your wallpaper. Off = Monk's own blue and violet.", "Палитра Material You из ваших обоев. Выкл = фирменные синий и фиолетовый Monk.")
    val data get() = t("Data", "Данные")
    val resetStats get() = t("Reset statistics", "Сбросить статистику")
    val resetStatsConfirm get() = t("Delete all statistics? Apps and settings stay.", "Удалить всю статистику? Приложения и настройки останутся.")
    val delete get() = t("Delete", "Удалить")
    val about get() = t("About", "О приложении")
    fun version(v: String) = t("Version $v", "Версия $v")
    val checkUpdates get() = t("Check for updates", "Проверить обновления")
    val updateChecking get() = t("Checking…", "Проверяю…")
    val updateUpToDate get() = t("You are on the latest version.", "У вас последняя версия.")
    fun updateAvailable(v: String) = t("Monk $v is available", "Доступен Monk $v")
    fun updateSize(mb: String) = t("$mb MB, from GitHub Releases", "$mb МБ, из GitHub Releases")
    val updateInstall get() = t("Update", "Обновить")
    val updateRetry get() = t("Retry", "Повторить")
    fun updateDownloading(pct: Int) = t("Downloading… $pct%", "Загружаю… $pct%")
    val updateVerifying get() = t("Verifying checksum…", "Проверяю контрольную сумму…")
    val updateInstalling get() = t("Installing…", "Устанавливаю…")
    val updateNeedsPermission get() = t("Allow Monk to install updates, then come back.", "Разрешите Monk устанавливать обновления и вернитесь.")
    val updateAllow get() = t("Allow", "Разрешить")
    fun updateFailed(reason: String) = t("Update failed: $reason", "Обновление не удалось: $reason")
    val releaseNotes get() = t("What's new", "Что нового")
    val aboutText get() = t(
        "Monk puts a moment of friction between you and the apps you open on autopilot. Everything stays on the device; the network is used only to check GitHub for updates.",
        "Monk ставит секунду трения между вами и приложениями, которые открываются на автопилоте. Всё остаётся на телефоне; сеть нужна только для проверки обновлений на GitHub.",
    )
    val language get() = t("Language follows the system", "Язык берётся из системы")
    val dayShort get() = if (ru) listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс") else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    // Stats
    val statsToday get() = t("Today", "Сегодня")
    val statsWeek get() = t("7 days", "7 дней")
    val statsAllTime get() = t("All time", "Всё время")
    val intercepted get() = t("Paused", "Остановок")
    val turnedAway get() = t("Walked away", "Ушли")
    val opened get() = t("Opened anyway", "Открыли")
    val statsEmpty get() = t("Nothing intercepted yet.", "Пока ни одной остановки.")
    val byDay get() = t("By day", "По дням")
    val byApp get() = t("By app", "По приложениям")
    val byHour get() = t("By hour of day", "По часам")
    val byHourHint get() = t("When you reach for these apps.", "Когда тянет к этим приложениям.")
    val reasons get() = t("Why you opened", "Зачем открывали")
    fun intention(i: Intention) = when (i) {
        Intention.REPLY -> t("Reply to someone", "Ответить кому-то")
        Intention.LOOKUP -> t("Check something specific", "Посмотреть конкретное")
        Intention.BORED -> t("Bored", "Скучно")
        Intention.HABIT -> t("Habit", "По привычке")
    }

    // Intercept
    val interceptBreathe get() = t("Breathe", "Вдох. Выдох.")
    val interceptQuestion get() = t("Do you really want to open", "Правда хотите открыть")
    fun interceptQuestionApp(label: String) = "$label?"
    fun interceptBlockedTitle(label: String) = t("$label is blocked", "$label под запретом")
    val interceptBlockedHint get() = t("You chose this earlier. Future you says thanks.", "Вы сами так решили. Будущий вы скажет спасибо.")
    fun interceptLimitTitle(label: String) = t("$label is done for today", "$label на сегодня всё")
    fun interceptLimitHint(limit: Int) = t("Daily limit of $limit reached. Opens again after midnight.", "Лимит $limit в день исчерпан. Снова после полуночи.")
    val interceptWhy get() = t("Why?", "Зачем?")
    val interceptNotNow get() = t("Not now", "Не сейчас")
    val interceptBack get() = t("Back to focus", "Вернуться к делу")
    fun interceptWait(seconds: Int) = t("Wait ${seconds}s", "Подождите ${seconds}с")
    fun interceptFocusTitle(time: String) = t("Focus until $time", "Фокус до $time")
    val interceptFocusHint get() = t("You started this session. Everything waits.", "Вы сами начали эту сессию. Всё подождёт.")
    fun timesToday(n: Int) = t(
        when (n) { 1 -> "1st time today"; 2 -> "2nd time today"; 3 -> "3rd time today"; else -> "${n}th time today" },
        "$n-й раз сегодня",
    )

    val iosTitle get() = t("iOS is not supported yet", "iOS пока не поддерживается")
    val iosBody get() = t(
        "Apple keeps app interception behind the Screen Time API. This build only ships the Android blocker; the iOS shell is a placeholder.",
        "Apple прячет перехват приложений за Screen Time API. В этой сборке блокировщик только для Android, оболочка iOS — заглушка.",
    )

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val n10 = n % 10
        val n100 = n % 100
        return when {
            n10 == 1 && n100 != 11 -> one
            n10 in 2..4 && n100 !in 12..14 -> few
            else -> many
        }
    }
}

val LocalStrings = staticCompositionLocalOf { Strings(ru = false) }

fun stringsForSystem() = Strings(ru = systemLanguage().lowercase().startsWith("ru"))

val strings: Strings
    @Composable get() = LocalStrings.current
