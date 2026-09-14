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
    val compactOn get() = t("Protection on", "Защита включена")
    val compactOff get() = t("Protection off", "Защита выключена")
    val compactBySchedule get() = t("Off by schedule", "Выключена по расписанию")
    fun compactUntil(what: String, time: String) = t("$what · until $time", "$what · до $time")
    fun compactApps(n: Int) = t(if (n == 1) "1 app on your list" else "$n apps on your list", "$n ${plural(n, "приложение", "приложения", "приложений")} в списке")
    val protectionPaused get() = t("Off by schedule", "Выключена по расписанию")
    fun pausedUntil(time: String) = t("Break until $time", "Перерыв до $time")
    fun strictUntil(time: String) = t("Strict mode until $time", "Строгий режим до $time")
    val pauseFor get() = t("Break", "Перерыв")
    val pause15 get() = t("15 min", "15 мин")
    val pause60 get() = t("1 hour", "1 час")
    val pauseDay get() = t("Until tomorrow", "До завтра")
    val resume get() = t("Resume", "Возобновить")
    val focus get() = t("Focus", "Фокус")
    val focus15 get() = t("15 min", "15 мин")
    val focus30 get() = t("30 min", "30 мин")
    val focus45 get() = t("45 min", "45 мин")
    val focus60 get() = t("1 hour", "1 час")
    val pause5 get() = t("5 min", "5 мин")
    val breakWhat get() = t(
        "A break switches protection off for a set time: every app on your list opens freely, then protection comes back by itself. Starting one takes the same ten-second breath as switching off, and the next break waits half an hour after the last one ends. Locked apps stay protected.",
        "Перерыв выключает защиту на время: все приложения из списка открываются свободно, потом защита включается сама. Перед началом — тот же десятисекундный выдох, что и при выключении, а следующий перерыв можно взять через полчаса после конца прошлого. Запертые приложения остаются под защитой.",
    )
    val breakConfirmTitle get() = t("Take a break?", "Взять перерыв?")
    fun breakConfirmBody(time: String) = t("Protection is off until $time: every app on your list opens freely. Breathe first — the same pause you asked for.", "Защита выключится до $time: все приложения из списка будут открываться свободно. Сначала выдох — та же пауза, о которой вы просили.")
    val startBreak get() = t("Start break", "Начать перерыв")
    fun breakCooldown(time: String) = t("Next break from $time", "Следующий перерыв — с $time")
    val focusWhat get() = t("A focus session is the opposite: every app on your list is blocked outright until the timer ends, and it cannot be stopped early.", "Фокус — наоборот: все приложения из списка полностью закрыты до конца таймера, и остановить его раньше нельзя.")
    val offConfirmTitle get() = t("Switch protection off?", "Выключить защиту?")
    val offConfirmBody get() = t("No timer, no way back except this switch. Breathe first — the same pause you asked for.", "Без таймера и без обратного пути, кроме этого же переключателя. Сначала выдох — та же пауза, о которой вы просили.")
    fun countdownWait(seconds: Int) = t("Wait ${seconds}s", "Ещё ${seconds} с")
    val switchOff get() = t("Switch off", "Выключить")
    val lockedStayOn get() = t("Locked apps stay protected.", "Запертые приложения остаются под защитой.")
    fun focusNoStop(time: String) = t("Focus runs until $time and cannot be stopped.", "Фокус идёт до $time, остановить его нельзя.")
    fun strictNoChange(time: String) = t("Strict mode until $time: nothing can be softened.", "Строгий режим до $time: смягчить ничего нельзя.")
    val inhale get() = t("inhale", "вдох")
    val exhale get() = t("exhale", "выдох")
    fun focusUntil(time: String) = t("Focus until $time", "Фокус до $time")
    fun focusLeft(minutes: Int) = t("Focus · $minutes min left", "Фокус · ещё $minutes мин")
    fun pauseLeft(minutes: Int) = t("Break · $minutes min left", "Перерыв · ещё $minutes мин")
    val focusHint get() = t("Every app on your list is blocked until the timer ends. No way to stop early.", "Все приложения из списка закрыты до конца таймера. Остановить раньше нельзя.")
    val focusConfirmTitle get() = t("Start a focus session?", "Начать фокус?")
    fun focusConfirmBody(time: String) = t("Every app on your list stays blocked until $time. No way to stop early.", "Все приложения из списка будут закрыты до $time. Остановить раньше нельзя.")
    val start get() = t("Start", "Начать")
    val todayForApp get() = t("Today", "Сегодня")
    val noData get() = t("No data for this period.", "За этот период данных нет.")
    fun pauses(n: Int) = t(if (n == 1) "1 pause" else "$n pauses", "$n ${plural(n, "пауза", "паузы", "пауз")}")
    val strictLocked get() = t("Locked by strict mode", "Закрыто строгим режимом")
    val strictShort get() = t("Strict", "Строгий режим")
    fun until(time: String) = t("Until $time", "До $time")
    val cannotStop get() = t("cannot be stopped", "остановить нельзя")
    val comesBackItself get() = t("protection comes back by itself", "защита включится сама")
    fun appsWatched(n: Int) = t(if (n == 1) "1 app on your list gets a pause" else "$n apps on your list get a pause", "$n ${plural(n, "приложение из списка ждёт", "приложения из списка ждут", "приложений из списка ждут")} паузы")
    val offNudge get() = t("Every app on your list opens freely. The switch brings the pause back.", "Все приложения из списка открываются свободно. Переключатель вернёт паузу.")
    val scheduleOffNudge get() = t("Off by schedule; it comes back on its own.", "Выключена по расписанию; включится сама.")
    fun lockedCount(n: Int) = t(if (n == 1) "1 locked" else "$n locked", "Заперто: $n")

    // Onboarding / help
    val howTitle get() = t("How Monk works", "Как работает Monk")
    val howStep1 get() = t("1. Pick the apps you open on autopilot.", "1. Выберите приложения, которые открываете на автопилоте.")
    val howStep2 get() = t("2. When you open one, Monk shows a pause screen with a short countdown instead of the app.", "2. Вместо такого приложения откроется экран паузы с коротким отсчётом.")
    val howStep3 get() = t("3. After the countdown you choose: open it for a few minutes, or walk away.", "3. После отсчёта решаете: открыть на несколько минут или не открывать.")
    val howStep4 get() = t("Block mode skips the choice: the app just does not open.", "В режиме «Запрет» выбора нет: приложение просто не открывается.")
    val gotIt get() = t("Got it", "Понятно")
    val learnMore get() = t("Why it works", "Почему это работает")
    val howLoopTitle get() = t("The loop you are in", "Петля, в которой вы застряли")
    val howLoopBody get() = t(
        "Cue → craving → action → reward. A dull moment, a buzz, a glance at the icon — and the thumb has already opened the feed. The reward is unpredictable (sometimes there is something, mostly nothing), and that is exactly what keeps the loop tight. The opening itself has become a reflex, with no decision in it.",
        "Сигнал → тяга → действие → награда. Скучная минута, вибрация, взгляд на иконку — и палец уже открыл ленту. Награда непредсказуема: иногда там что-то есть, чаще ничего, и именно это держит петлю туго. Само открытие давно стало рефлексом, решения в нём нет.",
    )
    val howWhyTitle get() = t("Why a pause works", "Почему пауза работает")
    val howWhyBody get() = t(
        "The reflex needs the reward to come instantly. A few seconds of nothing put a gap between the urge and the feed, and in that gap the deciding part of you wakes up. The craving itself peaks and fades within seconds; most of the time, when the countdown ends, you no longer want in. Over weeks the phone stops promising an instant hit, and you reach for it less.",
        "Рефлексу нужна мгновенная награда. Несколько секунд пустоты вставляют зазор между тягой и лентой, и в этом зазоре просыпается та часть вас, которая решает. Сама тяга достигает пика и спадает за секунды: чаще всего, когда отсчёт кончается, заходить уже не хочется. За недели телефон перестаёт обещать мгновенный кайф, и рука тянется к нему реже.",
    )
    val howDataTitle get() = t("What the data says", "Что показали данные")
    val howDataBody get() = t(
        "A 2023 study in PNAS (Grüning, Riedel, Lorenz-Spreen; 280 people, six weeks) tested exactly this kind of delay screen. People abandoned 36% of the app openings they had started, tried to open those apps 37% less often, and within six weeks the attempts fell by 57%.",
        "Исследование 2023 года в PNAS (Grüning, Riedel, Lorenz-Spreen; 280 человек, шесть недель) проверяло именно такой экран задержки. Люди отказывались от 36% уже начатых открытий, пытались открыть эти приложения на 37% реже, а за шесть недель попыток стало меньше на 57%.",
    )
    val howUseTitle get() = t("How to use Monk", "Как пользоваться")
    val howStatesTitle get() = t("Protection states", "Состояния защиты")
    val howStatesBody get() = t(
        "On — the apps on your list open only through the pause screen.\n" +
            "Break — protection is off for a set time; starting one takes a ten-second breath, and the next break waits half an hour after the last.\n" +
            "Focus — every app on your list is blocked outright until the timer ends; it cannot be stopped early.\n" +
            "Strict mode — until the chosen time nothing can be softened: no off, no break, no removing apps.\n" +
            "Locked app — ignores off and breaks; only removing it from the list frees it.\n" +
            "Off — everything opens; switching off takes the same ten-second breath.",
        "Включена — приложения из списка открываются только через экран паузы.\n" +
            "Перерыв — защита выключена на время; перед стартом десять секунд выдоха, следующий перерыв — через полчаса после прошлого.\n" +
            "Фокус — все приложения из списка полностью закрыты до конца таймера, остановить его нельзя.\n" +
            "Строгий режим — до выбранного времени ничего нельзя смягчить: ни выключить, ни взять перерыв, ни убрать приложение.\n" +
            "Запертое приложение — не замечает ни выключения, ни перерывов; освободить его можно, только удалив из списка.\n" +
            "Выключена — всё открывается; перед выключением те же десять секунд выдоха.",
    )
    val loopSteps: List<String> get() = if (ru) listOf("сигнал", "тяга", "действие", "награда") else listOf("cue", "craving", "action", "reward")
    val haptics get() = t("Haptic feedback", "Тактильный отклик")
    val hapticsHint get() = t("A light tick on sliders, toggles and choices.", "Лёгкий отклик на ползунках, переключателях и выборе.")
    val liveStatus get() = t("Timer in the shade", "Таймер в шторке")
    val liveStatusHint get() = t("An ongoing notification with the focus or break countdown.", "Постоянное уведомление с отсчётом фокуса или перерыва.")
    fun liveFocusTitle(app: String) = t("Focus session", "Фокус")
    val liveFocusBody get() = t("Every app on your list is blocked", "Все приложения из списка закрыты")
    val liveBreakTitle get() = t("Break", "Перерыв")
    val liveBreakBody get() = t("Protection is off for now", "Защита пока выключена")
    val suggested get() = t("Usual suspects", "Обычные подозреваемые")
    val suggestedHint get() = t("Installed on this phone and known to eat time.", "Уже стоят на телефоне и известны тем, что съедают время.")
    val allApps get() = t("All apps", "Все приложения")
    val pauseFocusHint get() = t("A break switches protection off for a while. Focus blocks every app on your list until the timer ends.", "Перерыв выключает защиту на время. Фокус закрывает все приложения из списка до конца таймера.")
    val delayLengthHint get() = t("The countdown before the app may open.", "Отсчёт перед тем, как приложение можно будет открыть.")
    val allowLengthHint get() = t("How long the app stays open before Monk asks again.", "Сколько приложение остаётся открытым, прежде чем Monk спросит снова.")
    val countersHint get() = t(
        "Paused — how often Monk stepped in. Walked away — you closed the pause without the app. Opened anyway — you went in after it. Pauses with no outcome yet are the faint part.",
        "Пауз — сколько раз Monk вмешался. Удержались — закрыли паузу, не открыв приложение. Открыли — зашли после паузы. Паузы без исхода показаны бледным.",
    )
    val pendingLegend get() = t("No outcome yet", "Без исхода")
    fun pendingCount(n: Int) = t(if (n == 1) "1 pause with no outcome yet" else "$n pauses with no outcome yet", "$n ${plural(n, "пауза", "паузы", "пауз")} пока без исхода")
    val languageTitle get() = t("Language", "Язык")
    val languageSystem get() = t("Auto", "Авто")

    // Setup
    val setupTitle get() = t("Turn on protection", "Включите защиту")
    val setupAccessibility get() = t("Accessibility service", "Специальные возможности")
    val setupAccessibilityHint get() = t(
        "Monk needs it to notice which app is in front. It never reads screen content.",
        "Так Monk узнаёт, какое приложение сейчас на экране. Содержимое экрана он не читает.",
    )
    val setupSteps: List<String> get() = if (ru) listOf(
        "Откройте «Скачанные приложения» (или «Установленные»)",
        "Выберите Monk и включите переключатель",
        "Нажмите «Разрешить» и вернитесь сюда",
    ) else listOf(
        "Open \"Downloaded apps\" (or \"Installed apps\")",
        "Choose Monk and turn it on",
        "Confirm \"Allow\" and come back",
    )
    val openAccessibilitySettings get() = t("Open accessibility settings", "Открыть специальные возможности")
    val addSuggested get() = t("Add all", "Добавить все")
    val selectSuggested get() = t("Select all", "Выбрать все")
    val chooseManually get() = t("Choose from list", "Выбрать из списка")
    val setupRestricted get() = t(
        "Android 13+: the switch is greyed out for apps installed outside the store. Tap it once anyway, then open App info → ⋮ → Allow restricted settings, and come back.",
        "Android 13+: у приложений не из магазина переключатель серый. Всё равно нажмите его один раз, затем откройте «О приложении» → ⋮ → «Разрешить ограниченные настройки» и вернитесь.",
    )
    val enable get() = t("Enable", "Включить")
    val appInfo get() = t("App info", "О приложении")
    val enabled get() = t("Enabled", "Включена")

    // Week summary
    val thisWeek get() = t("This week", "Эта неделя")
    fun weekLine(paused: Int, away: Int) = t("${pauses(paused)} · $away walked away", "${pauses(paused)} · удержались $away ${plural(away, "раз", "раза", "раз")}")
    fun successRate(pct: Int) = t("Walked away from $pct% of pauses", "Удержались в $pct% пауз")
    fun streak(days: Int) = t(if (days == 1) "Walked away 1 day in a row" else "Walked away $days days in a row", "Удерживаетесь $days ${plural(days, "день", "дня", "дней")} подряд")
    val noWeekData get() = t("No pauses yet this week.", "На этой неделе пауз ещё не было.")

    // Apps
    val blockedApps get() = t("Your apps", "Мои приложения")
    val noApps get() = t("Pick your apps", "Выберите приложения")
    val noAppsHint get() = t(
        "The ones that open themselves. Each gets a pause before it opens, or a hard block.",
        "Те, что открываются сами собой. Перед каждым — пауза или полный запрет.",
    )
    val addApps get() = t("Add apps", "Добавить")
    val search get() = t("Search", "Поиск")
    val done get() = t("Done", "Готово")
    val save get() = t("Save", "Сохранить")
    val cancel get() = t("Cancel", "Отмена")
    val back get() = t("Back", "Назад")
    val clearSearch get() = t("Clear search", "Очистить поиск")
    val remove get() = t("Remove", "Убрать")
    val pickerStrictHint get() = t("Strict mode: apps already on your list cannot be unchecked.", "Строгий режим: приложения, которые уже в списке, снять нельзя.")
    val loadingApps get() = t("Reading installed apps…", "Загружаю список приложений…")
    val settingsKept get() = t("Settings kept from last time", "Настройки с прошлого раза сохранены")
    val notOnPhone get() = t("Not on this phone", "Нет на телефоне")
    val uninstalledHint get() = t("Removed from the phone. Its settings wait here and come back with the app.", "Приложение удалено с телефона. Настройки ждут здесь и вернутся вместе с ним.")
    val forget get() = t("Forget", "Забыть")
    fun openUntil(time: String) = t("Open until $time", "Открыто до $time")
    val endNow get() = t("End now", "Закрыть")
    fun limitToday(used: Int, limit: Int) = t("$used/$limit today", "$used/$limit сегодня")

    // App detail
    val modeTitle get() = t("Mode", "Режим")
    val modeBlock get() = t("Block", "Запрет")
    val modeBlockHint get() = t("Never opens while protection is on.", "Не открывается, пока включена защита.")
    val modeDelay get() = t("Pause", "Пауза")
    val modeDelayHint get() = t("Breathe first, then decide.", "Сначала выдох, потом решение.")
    val delayLength get() = t("Pause length", "Длительность паузы")
    val allowLength get() = t("Open for", "На сколько открывать")
    val dailyLimit get() = t("Daily limit", "Лимит в день")
    val dailyLimitHint get() = t("After this many opens the app is blocked until midnight.", "Когда открытия закончатся, приложение закрыто до полуночи.")
    val noLimit get() = t("No limit", "Без лимита")
    val useDefault get() = t("Same as in Settings", "Как в общих настройках")
    fun currently(v: String) = t("Currently $v", "Сейчас: $v")
    val seconds get() = t("s", "с")
    val minutes get() = t("min", "мин")
    val times get() = t("×", "×")
    fun pauseChip(seconds: Int) = t("${seconds}s pause", "Пауза ${seconds} с")
    fun openFor(minutes: Int) = t("Open for $minutes min", "Открыть на $minutes мин")

    // Rules & lock
    val rulesTitle get() = t("Rules by time", "Правила по времени")
    val rulesHint get() = t("Override the mode on certain days and hours. Overlaps resolve to the strictest.", "Меняют режим в выбранные дни и часы. Если правила пересекаются, действует самое строгое.")
    val noRules get() = t("No rules yet.", "Правил пока нет.")
    val addRule get() = t("Add rule", "Добавить правило")
    val ruleNew get() = t("New rule", "Новое правило")
    val ruleEdit get() = t("Edit rule", "Правило")
    val ruleBlock get() = t("Block", "Запрет")
    val rulePause get() = t("Pause", "Пауза")
    val ruleFree get() = t("Free", "Свободно")
    val ruleBlockHint get() = t("Does not open at all in this window.", "В это время не открывается совсем.")
    val rulePauseHint get() = t("The usual pause screen in this window.", "В это время — обычный экран паузы.")
    val ruleFreeHint get() = t("Opens without a pause in this window. The daily limit still counts.", "В это время открывается без паузы. Лимит в день всё равно считается.")
    val ruleNextDay get() = t("Ends the next day.", "Заканчивается на следующий день.")
    val ruleSameDay get() = t("Same day.", "В тот же день.")
    val ruleNextDayShort get() = t("(+1 day)", "(+1 день)")
    val everyDay get() = t("every day", "ежедневно")
    val resetRules get() = t("Remove all rules", "Удалить все правила")
    val lockTitle get() = t("Lock settings", "Запереть настройки")
    val lockHint get() = t("Mode, rules, pause and limit become read-only. The only way out is removing the app.", "Режим, правила, паузу и лимит будет не изменить. Единственный выход — удалить приложение.")
    val lockConfirmTitle get() = t("Lock these settings?", "Запереть настройки?")
    val lockConfirmBody get() = t("You will not be able to soften anything for this app. To undo, you will have to remove the app and add it again.", "Смягчить что-либо для этого приложения будет нельзя. Чтобы отменить, придётся удалить приложение и добавить заново.")
    val lock get() = t("Lock", "Запереть")
    val lockedNote get() = t("Locked. Remove the app to change anything.", "Настройки заперты. Чтобы что-то изменить, удалите приложение.")
    val removeAppTitle get() = t("Remove this app?", "Удалить приложение?")
    fun removeAppBody(rules: Int) = t(
        if (rules > 0) "Its $rules rule(s) and settings are kept and come back if you add it again. The lock is released." else "Its settings are kept and come back if you add it again.",
        if (rules > 0) "Настройки и правила ($rules) сохранятся и вернутся, если добавить его снова. Замок снимется." else "Настройки сохранятся и вернутся, если добавить его снова.",
    )
    fun interceptRuleTitle(label: String, until: String) = t("$label is closed until $until", "$label закрыт до $until")
    val interceptRuleHint get() = t("Your own rule for this time of day.", "Вы сами так настроили на это время.")
    val protectionHours get() = t("Protection hours", "Расписание защиты")
    val protectionHoursHint get() = t("Outside this window nothing is intercepted.", "Вне этих часов Monk ничего не перехватывает.")
    val system get() = t("System", "Система")

    // Settings
    val defaults get() = t("Defaults", "Общие настройки")
    val defaultsHint get() = t("For apps without their own setting.", "Для приложений без своих настроек.")
    val scheduleTitle get() = t("Schedule", "Расписание")
    val scheduleHint get() = t("Off means always.", "Выключено — значит всегда.")
    val scheduleAllDay get() = t("Same start and end = the whole day.", "Одинаковые начало и конец — весь день.")
    val from get() = t("Start", "Начало")
    val to get() = t("End", "Конец")
    val pauseScreen get() = t("Pause screen", "Экран паузы")
    val askIntention get() = t("Ask why", "Спрашивать, зачем")
    val askIntentionHint get() = t("Pick a reason before opening; it shows up in stats.", "Перед открытием — выбрать причину. Потом это видно в статистике.")
    val pauseMessage get() = t("Your line", "Ваша фраза")
    val pauseMessageHint get() = t("Your own line on the pause screen.", "Покажется на экране паузы.")
    val pauseMessagePlaceholder get() = t("e.g. Do you really need this now?", "Например: тебе это правда сейчас нужно?")
    val reliability get() = t("Reliability", "Надёжность")
    val overlayMode get() = t("Draw over everything", "Поверх всех окон")
    val overlayModeHint get() = t("For phones where the pause screen does not appear, and for split-screen.", "Если экран паузы не появляется или вы пользуетесь разделённым экраном.")
    val notifyWhenOff get() = t("Warn when protection stops", "Сообщать, если защита отключилась")
    val notifyWhenOffHint get() = t("A notification if the system switches the service off.", "Уведомление, если система остановит службу.")
    val notifyPermission get() = t("Allow notifications", "Разрешить уведомления")
    val notifyPromptBody get() = t("Android can stop the service after an update or a reboot. Allow notifications and Monk will tell you when protection is off.", "После обновления или перезагрузки Android может остановить службу. Разрешите уведомления, и Monk сообщит, когда защита отключится.")
    val notNow get() = t("Not now", "Не сейчас")
    val quickTiles get() = t("Quick Settings tiles", "Кнопки в шторке")
    val quickTilesHint get() = t("Break and focus from the notification shade.", "Перерыв и фокус прямо из шторки уведомлений.")
    val addTiles get() = t("Add tiles", "Добавить кнопки")
    val addMissingTile get() = t("Add the second one", "Добавить вторую")
    val tilesAdded get() = t("Both in the shade", "Обе в шторке")
    val tilesManual get() = t("Open the shade, tap the pencil and drag the two Monk tiles up.", "Откройте шторку, нажмите карандаш и перетащите две кнопки Monk наверх.")
    val tilePause get() = t("Monk break", "Monk: перерыв")
    val tileFocus get() = t("Monk focus", "Monk: фокус")
    // Keep it alive: the OS side of "the service must not die"
    val keepAliveTitle get() = t("Keep it running", "Чтобы защита не отключалась")
    val keepAliveHint get() = t(
        "Android and the phone maker can put a quiet app to sleep. Two switches keep Monk out of those lists.",
        "Android и производитель телефона усыпляют тихие приложения. Пара переключателей убирает Monk из этих списков.",
    )
    val battery get() = t("Battery", "Батарея")
    val batteryUnrestricted get() = t("Unrestricted", "Без ограничений")
    val batteryOptimized get() = t("Optimized: the system may put Monk to sleep.", "Оптимизируется: система может усыпить Monk.")
    val batteryRestricted get() = t("Restricted in the background (deep sleep). Set it to Unrestricted.", "Ограничено в фоне (глубокий сон). Переключите на «Без ограничений».")
    val batterySleeping get() = t("Put to sleep by the system. Set it to Unrestricted.", "Система усыпила приложение. Переключите на «Без ограничений».")
    val allow get() = t("Allow", "Разрешить")
    val fix get() = t("Fix", "Исправить")
    val samsungSleep get() = t("Never sleeping apps", "Не переводить в спящий режим")
    val samsungSleepHint get() = t("Device care → Battery → Background usage limits: add Monk to \"Never sleeping apps\".", "Обслуживание устройства → Батарея → Ограничения фоновой активности: добавьте Monk в «Никогда не переводить в спящий режим».")
    val open get() = t("Open", "Открыть")
    val serviceOffTitle get() = t("Monk protection stopped", "Защита Monk отключилась")
    val serviceOffBody get() = t("The accessibility service is off, so your apps open freely. Tap to turn it back on.", "Служба специальных возможностей выключена, приложения из списка открываются без паузы. Нажмите, чтобы включить снова.")
    val strictTitle get() = t("Strict mode", "Строгий режим")
    val strictHint get() = t(
        "Until the chosen time you cannot turn protection off, take a break, remove apps or soften their settings. No way back.",
        "До выбранного времени нельзя выключить защиту, взять перерыв, убрать приложения или смягчить настройки. Отменить невозможно.",
    )
    val strictUntilMidnight get() = t("Until midnight", "До полуночи")
    val strict1h get() = t("For 1 hour", "На 1 час")
    val strictCustom get() = t("Choose", "Выбрать")
    fun strictStart(time: String) = t("Lock until $time", "Закрыть до $time")
    fun forDuration(text: String) = t("For $text", "На $text")
    val strictConfirmTitle get() = t("Enable strict mode?", "Включить строгий режим?")
    fun strictConfirmBody(time: String) = t("Nothing can be softened until $time. Not even by you.", "До $time ничего нельзя смягчить. Даже вам.")
    val confirm get() = t("Enable", "Включить")
    val appearance get() = t("Appearance", "Оформление")
    val themeSystem get() = t("Auto", "Авто")
    val themeLight get() = t("Light", "Светлая")
    val themeDark get() = t("Dark", "Тёмная")
    val dynamicColor get() = t("Wallpaper colors", "Цвета обоев")
    val dynamicColorHint get() = t("Material You palette from your wallpaper.", "Палитра Material You из ваших обоев.")
    val data get() = t("Data", "Данные")
    val resetStats get() = t("Reset statistics", "Сбросить статистику")
    val resetStatsConfirm get() = t("Delete all statistics? Apps and settings stay.", "Удалить всю статистику? Приложения и настройки останутся.")
    val delete get() = t("Delete", "Удалить")
    val about get() = t("About", "О приложении")
    val madeWithUrl get() = "https://mikeportnov.com"
    val sourceUrl get() = "https://github.com/mdportnov/monk-cli"
    val aboutTagline get() = t("A second of friction before the apps you open on autopilot.", "Секунда трения перед приложениями, которые вы открываете на автопилоте.")
    val aboutPrivacy get() = t("Everything stays on the phone", "Всё остаётся на телефоне")
    val aboutPrivacyHint get() = t("No account, no analytics. The network is used only to check GitHub for updates.", "Без аккаунта и аналитики. Интернет нужен только для проверки обновлений на GitHub.")
    val aboutAccessibility get() = t("Reads only which app is in front", "Видит только, какое приложение на экране")
    val aboutAccessibilityHint get() = t("The accessibility service never reads screen content.", "Служба специальных возможностей не читает содержимое экрана.")
    val aboutSource get() = t("Open source", "Открытый код")
    val aboutSourceHint get() = t("Part of the monk CLI project on GitHub.", "Часть проекта monk CLI на GitHub.")
    fun version(v: String) = t("Version $v", "Версия $v")
    val checkUpdates get() = t("Check for updates", "Проверить обновления")
    val updateChecking get() = t("Checking…", "Проверяю…")
    val updateUpToDate get() = t("You are on the latest version.", "У вас последняя версия.")
    fun updateAvailable(v: String) = t("Monk $v is available", "Доступен Monk $v")
    fun updateSize(mb: String) = t("$mb MB, from GitHub Releases", "$mb МБ, с GitHub Releases")
    val updateInstall get() = t("Update", "Обновить")
    val updateRetry get() = t("Retry", "Повторить")
    fun updateDownloading(pct: Int) = t("Downloading… $pct%", "Загружаю… $pct%")
    val updateVerifying get() = t("Verifying checksum…", "Проверяю контрольную сумму…")
    val updateInstalling get() = t("Installing…", "Устанавливаю…")
    val updateNeedsPermission get() = t("Allow Monk to install updates, then come back.", "Разрешите Monk устанавливать обновления и вернитесь.")
    val updateAllow get() = t("Allow", "Разрешить")
    fun updateFailed(reason: String) = t("Update failed: $reason", "Не удалось обновить: $reason")
    val releaseNotes get() = t("What's new", "Что нового")
    val language get() = t("The pause screen, tiles and notifications follow.", "Экран паузы, кнопки в шторке и уведомления тоже переключатся.")
    val dayShort: List<String> = if (ru) listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс") else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    // Stats
    val statsToday get() = t("Today", "Сегодня")
    val statsWeek get() = t("7 days", "7 дней")
    val statsAllTime get() = t("All time", "Всё время")
    val intercepted get() = t("Paused", "Пауз")
    val turnedAway get() = t("Walked away", "Удержались")
    val opened get() = t("Opened anyway", "Открыли")
    val statsEmpty get() = t("No pauses yet. The numbers appear after Monk steps in for the first time.", "Пауз ещё не было. Цифры появятся после первой паузы Monk.")
    val byDay get() = t("By day", "По дням")

    // Screen time (usage access)
    val screenTime get() = t("Screen time", "Экранное время")
    val screenTimeOptInTitle get() = t("See where the time goes", "Куда уходит время")
    val screenTimeOptInBody get() = t(
        "Android keeps a record of how long each app is on screen — the same one Digital Wellbeing shows. With Usage access, Monk can show it here for the apps on your list, next to your pauses. The data stays on the phone.",
        "Android ведёт учёт, сколько каждое приложение было на экране, — это те же цифры, что в «Цифровом благополучии». Если разрешить доступ к статистике, Monk покажет их здесь для приложений из списка, рядом с паузами. Данные никуда не уходят с телефона.",
    )
    val allowUsageAccess get() = t("Allow usage access", "Разрешить доступ")
    val usageAccess get() = t("Usage access", "Доступ к статистике")
    val usageAccessOn get() = t("Screen time is shown in Stats.", "Экранное время показывается в статистике.")
    val usageAccessOff get() = t("Lets Stats show how long the apps on your list were on screen.", "Позволяет показывать в статистике, сколько приложения из списка были на экране.")
    val screenTimeWatched get() = t("On your list", "Приложения из списка")
    val wholePhone get() = t("Whole phone", "Весь телефон")
    fun screenTimeShare(percent: Int) = t("$percent% of your phone time", "$percent% всего времени с телефоном")
    val screenTimeEmpty get() = t("Nothing from your list was on screen.", "Приложения из списка на экран не выходили.")
    fun screenTimeCoverage(days: Int) = t("The phone keeps this record for the last $days days.", "Телефон хранит такую запись за последние $days ${plural(days, "день", "дня", "дней")}.")
    fun screenTimeLastDays(days: Int) = t("Last $days days", "Последние $days ${plural(days, "день", "дня", "дней")}")
    fun perDayAverage(d: String) = t("about $d a day", "в среднем $d в день")
    fun lessThan(d: String, than: String) = t("$d less than $than", "на $d меньше, чем $than")
    fun moreThan(d: String, than: String) = t("$d more than $than", "на $d больше, чем $than")
    fun sameAs(than: String) = t("about the same as $than", "примерно как $than")
    val yesterdayWord get() = t("yesterday", "вчера")
    val lastWeekWord get() = t("last week", "на прошлой неделе")
    val todayWord get() = t("today", "сегодня")
    val thisWeekWord get() = t("this week", "за неделю")
    fun screenTimeWeek(today: String, week: String) = t("On screen: $today today · $week this week", "На экране: $today сегодня · $week за неделю")
    fun screenTimeTodayLine(phone: String, watched: String) = t("On screen today: $phone · $watched on your list", "На экране сегодня: $phone · $watched из списка")
    val onYourList get() = t("on your list", "в списке")
    val addToList get() = t("Add", "В список")
    val showAll get() = t("Show all", "Показать всё")
    val stats30 get() = t("30 days", "30 дней")
    val last30Word get() = t("in 30 days", "за 30 дней")
    val previous30Word get() = t("the 30 days before", "предыдущие 30 дней")
    fun recordedSince(date: String) = t("On record since $date.", "Запись ведётся с $date.")
    fun screenTimeListShare(percent: Int) = t("$percent% on your list", "$percent% — приложения из списка")
    fun notRecordedBefore(date: String) = t("Days before $date are not on record: the phone had already forgotten them when Monk first looked.", "Дни до $date не записаны: телефон уже забыл их, когда Monk посмотрел в первый раз.")
    val screenTimeNothing get() = t("Nothing was on screen.", "Ничего не было на экране.")
    val screenTimeNoRecord get() = t("No record for this period.", "За этот период записи нет.")
    val perDay get() = t("Per day", "По дням")
    val averagePerDay get() = t("A day on average", "В среднем за день")
    val longestDay get() = t("Longest day", "Самый долгий день")
    val quietestDay get() = t("Quietest day", "Самый тихий день")
    val daysOnRecord get() = t("Days on record", "Дней в записи")
    val notOpened get() = t("not opened", "не открывали")
    val monkPauses get() = t("Monk pauses", "Паузы Monk")
    val notWatchedHint get() = t("Not on your list: Monk does not pause it. Add it to see pauses next to the time.", "Не в списке: Monk не ставит его на паузу. Добавьте, чтобы видеть паузы рядом со временем.")
    val clearDay get() = t("All days", "Все дни")
    fun dayDate(date: String): String {
        val p = date.split("-")
        val m = p[1].toInt()
        val d = p[2].toInt()
        val months = if (ru) listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
        else listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return "$d ${months[m - 1]}"
    }
    fun durationOf(hours: Int, minutes: Int): String = when {
        hours > 0 && minutes > 0 -> t("$hours h $minutes min", "$hours ч $minutes мин")
        hours > 0 -> t("$hours h", "$hours ч")
        else -> t("$minutes min", "$minutes мин")
    }
    val underMinute get() = t("< 1 min", "< 1 мин")
    fun duration(millis: Long): String {
        val minutes = (millis / 60_000L).toInt()
        if (minutes < 1) return underMinute
        return durationOf(minutes / 60, minutes % 60)
    }
    val byApp get() = t("By app", "По приложениям")
    val byHour get() = t("By hour of day", "По часам")
    val byHourHint get() = t("Pauses by hour of the day: the taller the bar, the more often you reached for these apps at that hour.", "Паузы по часам: чем выше столбик, тем чаще в это время тянуло к этим приложениям.")
    val byAppHint get() = t("Percent — the pauses you walked away from. Bar — pauses, longest for the busiest app.", "Процент — доля пауз, в которых удержались. Полоска — паузы, самая длинная у самого частого приложения.")
    val reasons get() = t("Why you opened", "Зачем открывали")
    val reasonsHint get() = t("Your answers to «Why?» on the pause screen when you opened anyway.", "Ваши ответы на «Зачем?» на экране паузы, когда всё же открывали.")
    val screenTimeHint get() = t("Your list — the apps Monk pauses. Whole phone — every app, as the system counts it.", "Список — приложения, которые Monk ставит на паузу. Весь телефон — все приложения, как считает система.")
    val appTimeHint get() = t("Bar — share of the phone's time. «Add» puts the app on your list, and Monk starts pausing it.", "Полоска — доля времени с телефоном. «В список» — Monk начнёт ставить приложение на паузу.")
    val deltaHint get() = t("+/− — change against the previous period of the same length.", "+/− — разница с предыдущим периодом такой же длины.")
    val averagesHint get() = t("Averages count only days on record.", "Средние считаются только по дням с записью.")
    fun intention(i: Intention) = when (i) {
        Intention.REPLY -> t("Reply to someone", "Ответить")
        Intention.LOOKUP -> t("Look something up", "Найти нужное")
        Intention.BORED -> t("Bored", "Скучно")
        Intention.HABIT -> t("Habit", "По привычке")
    }

    // Intercept
    val interceptBreathe get() = t("Breathe", "Дышите")
    val interceptQuestion get() = t("Do you really want to open", "Точно открыть")
    fun interceptQuestionApp(label: String) = "$label?"
    fun interceptBlockedTitle(label: String) = t("$label is blocked", "$label под запретом")
    val interceptBlockedHint get() = t("You chose this earlier. Future you says thanks.", "Вы сами так решили. Будущий вы скажет спасибо.")
    fun interceptLimitTitle(label: String) = t("$label is done for today", "$label: на сегодня всё")
    fun interceptLimitHint(limit: Int) = t("Daily limit of $limit reached. Opens again after midnight.", "Лимит: $limit ${plural(limit, "открытие", "открытия", "открытий")} в день. Дальше — после полуночи.")
    val interceptWhy get() = t("Why?", "Зачем?")
    val interceptNotNow get() = t("Not now", "Не сейчас")
    val interceptBack get() = t("Back to focus", "К делу")
    fun interceptWait(seconds: Int) = t("Wait ${seconds}s", "Ещё $seconds с")
    fun interceptFocusTitle(time: String) = t("Focus until $time", "Фокус до $time")
    val interceptFocusHint get() = t("You started this session. Everything waits.", "Вы сами начали фокус. Всё подождёт.")
    fun timesToday(n: Int) = t("${ordinal(n)} time today", "Сегодня уже $n-й раз")

    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

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

/** "system" follows the device; otherwise a language tag. */
fun stringsFor(language: String): Strings = when (language) {
    "ru" -> Strings(ru = true)
    "en" -> Strings(ru = false)
    else -> stringsForSystem()
}

val strings: Strings
    @Composable get() = LocalStrings.current
