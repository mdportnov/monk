package com.mdportnov.monk.shared.ui.home

import com.mdportnov.monk.shared.platform.systemLanguage

/** One short line a day. Same index in both lists, picked by the day of the year. */
object Quotes {
    private val en = listOf(
        "The feed will still be there in 60 minutes. So will you.",
        "Attention is the only thing you actually own.",
        "Boredom is where ideas come from.",
        "You are not missing anything. That's the trick.",
        "Reach for the phone, notice the reach.",
        "10 seconds of nothing beat 10 minutes of scrolling.",
        "The app wants your time. Decide what you want.",
        "Quiet is not empty. It's room.",
        "One pause at a time.",
        "Whatever it is, it can wait until you choose it.",
        "The urge passes. It always does.",
        "Look up. That's the whole practice.",
    )
    private val ru = listOf(
        "Лента никуда не денется через 60 минут. И вы тоже.",
        "Внимание — единственное, что по-настоящему ваше.",
        "Скука — это место, откуда приходят идеи.",
        "Вы ничего не пропускаете. В этом и фокус.",
        "Тянетесь к телефону — заметьте это движение.",
        "10 секунд тишины лучше 10 минут ленты.",
        "Приложению нужно ваше время. Решите, что нужно вам.",
        "Тишина — не пустота. Это место для себя.",
        "Одна пауза за раз.",
        "Что бы это ни было, оно подождёт, пока вы сами не выберете.",
        "Желание проходит. Всегда.",
        "Поднимите глаза. В этом вся практика.",
    )

    fun of(language: String, dayOfYear: Int): String {
        val isRu = when (language) { "ru" -> true; "en" -> false; else -> systemLanguage().lowercase().startsWith("ru") }
        val list = if (isRu) ru else en
        return list[dayOfYear.mod(list.size)]
    }
}
