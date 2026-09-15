package com.mdportnov.monk.shared.model

import kotlinx.serialization.Serializable

/**
 * What a routine does to the apps it covers while it is open. There is deliberately no "free"
 * here: a routine is a second layer over the app's own setting and it may only ever tighten it,
 * so a routine can never become a way to open something that was closed.
 */
@Serializable
enum class RoutineMode { BLOCK, PAUSE }

/**
 * One weekly window of a routine. Same arithmetic as a per-app rule: [endMinute] <= [startMinute]
 * runs into the next day and the weekday is checked at the window's start, so a Friday 22:00–07:00
 * window covers Saturday 03:00. Equal start and end mean the whole day.
 */
@Serializable
data class RoutineWindow(
    val id: Long,
    /** ISO day numbers, Monday = 1 … Sunday = 7. */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
) {
    val crossesMidnight get() = endMinute <= startMinute && startMinute != endMinute
    val allDay get() = startMinute == endMinute
    fun isActive(dayIso: Int, minuteOfDay: Int) = TimeWindow.isActive(days, startMinute, endMinute, dayIso, minuteOfDay)

    fun repaired(): RoutineWindow = copy(
        days = days.filter { it in 1..7 }.toSet().ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) },
        startMinute = startMinute.coerceIn(0, TimeWindow.DAY - 1),
        endMinute = endMinute.coerceIn(0, TimeWindow.DAY - 1),
    )
}

/**
 * A named set of hours with one verdict: "🌙 Evening wind-down blocks everything from 22:00".
 * Four ship with the app and cannot be deleted — only changed, switched off, or put back the way
 * they came. Anything the user makes is theirs to delete.
 *
 * A routine is either open on its own schedule ([windows]) or started by hand for a while
 * (a [RoutineRun]); the two are the same routine and merge the same way. What separates them is
 * how firm they are: a scheduled routine is an ordinary layer that the master switch turns off
 * and a break lifts (unless [ignoresBreaks]), while a run is a commitment that nothing lifts
 * before its time — the promise a focus session has always made, generalised.
 */
@Serializable
data class Routine(
    val id: String,
    /** Ships with the app: cannot be deleted, can be reset. Set by the model, never by the caller. */
    val builtIn: Boolean = false,
    /** One emoji. Empty falls back to a drawn mark. */
    val emoji: String = "",
    /** Empty on a built-in means "use the name this build ships in the user's language". */
    val name: String = "",
    val mode: RoutineMode = RoutineMode.BLOCK,
    /** Every app on the list, now and in the future. */
    val allApps: Boolean = true,
    /** Only meaningful when [allApps] is off. Packages no longer watched are kept: re-adding the app re-joins the routine. */
    val packages: Set<String> = emptySet(),
    /** Empty means the routine never opens by itself; it can still be started by hand. */
    val windows: List<RoutineWindow> = emptyList(),
    val enabled: Boolean = false,
    /** A break lifts every other routine; this one holds. */
    val ignoresBreaks: Boolean = false,
    /** Minutes offered first when starting it by hand. */
    val manualMinutes: Int = 60,
) {
    /** It opens on its own at some hour. */
    val scheduled get() = windows.isNotEmpty()

    /** Its scope is a hand-picked list that has gone empty: it can never apply to anything. */
    val coversNothing get() = !allApps && packages.isEmpty()

    fun covers(packageName: String) = allApps || packageName in packages

    /** On, in scope of something, and inside one of its windows. */
    fun isOpen(dayIso: Int, minuteOfDay: Int) =
        enabled && !coversNothing && windows.any { it.isActive(dayIso, minuteOfDay) }

    /** The routine's verdict on the scale the policy merges everything on. */
    val ruleMode get() = if (mode == RoutineMode.BLOCK) RuleMode.BLOCK else RuleMode.PAUSE

    /**
     * Wall-clock minutes until the routine's windows stop covering this moment, walking up to a
     * week; null when it is not open now or never closes (every day, all day).
     */
    fun openUntilMinutes(dayIso: Int, minuteOfDay: Int): Int? {
        if (!isOpen(dayIso, minuteOfDay)) return null
        var day = dayIso
        var minute = minuteOfDay
        for (elapsed in 1..7 * TimeWindow.DAY) {
            minute++
            if (minute == TimeWindow.DAY) { minute = 0; day = TimeWindow.nextDay(day) }
            if (!isOpen(day, minute)) return elapsed
        }
        return null
    }

    /** Every minute of the week this routine is open, as a flat Monday-first array. */
    fun weekMask(): BooleanArray {
        val mask = BooleanArray(7 * TimeWindow.DAY)
        if (!enabled || windows.isEmpty()) return mask
        for (day in 1..7) {
            val base = (day - 1) * TimeWindow.DAY
            for (minute in 0 until TimeWindow.DAY) {
                if (windows.any { it.isActive(day, minute) }) mask[base + minute] = true
            }
        }
        return mask
    }

    /**
     * True when this version protects at least as much as [other] — same or stricter verdict, a
     * scope that is not narrower, a week that is not shorter, and no promise withdrawn. This is
     * the guard strict mode uses: under it a routine may be tightened freely and softened not
     * at all, so the routine cannot become the loophole strict mode exists to close.
     */
    fun isAtLeastAsStrictAs(other: Routine): Boolean {
        if (other.enabled && !enabled) return false
        if (other.mode == RoutineMode.BLOCK && mode != RoutineMode.BLOCK) return false
        if (other.ignoresBreaks && !ignoresBreaks) return false
        if (!allApps && (other.allApps || !packages.containsAll(other.packages))) return false
        val mine = weekMask()
        val theirs = other.weekMask()
        return theirs.indices.none { theirs[it] && !mine[it] }
    }

    /** Values a hand-edited or hand-written config cannot be trusted to have got right. */
    fun repaired(): Routine = copy(
        emoji = Emoji.firstCluster(emoji),
        name = name.trim().take(MAX_NAME),
        packages = if (allApps) packages else packages.filter { it.isNotBlank() }.toSet(),
        windows = windows.map { it.repaired() }.distinctBy { it.id }.take(MAX_WINDOWS),
        manualMinutes = manualMinutes.coerceIn(MIN_MANUAL_MINUTES, MAX_MANUAL_MINUTES),
    )

    companion object {
        const val MAX_NAME = 40
        const val MAX_WINDOWS = 8
        const val MIN_MANUAL_MINUTES = 5
        const val MAX_MANUAL_MINUTES = 24 * 60
        /** How many routines a person may keep, built-ins included; a guard, not a product limit. */
        const val MAX_ROUTINES = 40
    }
}

/**
 * A routine started by hand. One at a time: the running one can be extended, never swapped or
 * cut short, which is what makes it worth starting.
 */
@Serializable
data class RoutineRun(
    val routineId: String,
    val startedAt: Long,
    val until: Long,
)

/**
 * The routines every install has. They are seeded on first run and re-seeded if they go missing,
 * so a build that adds one gives it to everybody; ids are permanent.
 *
 * Only Focus ships switched on, and it has no windows: it is exactly today's focus session with
 * a name and a face. The other three are switched off, so an update changes nobody's behaviour
 * until they choose one.
 */
object BuiltInRoutines {
    const val FOCUS = "builtin:focus"
    const val MORNING = "builtin:morning"
    const val EVENING = "builtin:evening"
    const val WORK = "builtin:work"

    /** Factory settings, in the order they are listed. */
    val all: List<Routine> get() = listOf(
        Routine(
            id = FOCUS,
            builtIn = true,
            emoji = "🎯",
            mode = RoutineMode.BLOCK,
            windows = emptyList(),
            enabled = true,
            ignoresBreaks = true,
            manualMinutes = 30,
        ),
        Routine(
            id = MORNING,
            builtIn = true,
            emoji = "🌅",
            mode = RoutineMode.BLOCK,
            windows = listOf(RoutineWindow(id = 1, startMinute = 6 * 60, endMinute = 9 * 60)),
            enabled = false,
            manualMinutes = 60,
        ),
        Routine(
            id = EVENING,
            builtIn = true,
            emoji = "🌙",
            mode = RoutineMode.BLOCK,
            windows = listOf(RoutineWindow(id = 1, startMinute = 22 * 60, endMinute = 7 * 60)),
            enabled = false,
            manualMinutes = 60,
        ),
        Routine(
            id = WORK,
            builtIn = true,
            emoji = "💼",
            mode = RoutineMode.PAUSE,
            windows = listOf(RoutineWindow(id = 1, days = setOf(1, 2, 3, 4, 5), startMinute = 9 * 60, endMinute = 18 * 60)),
            enabled = false,
            manualMinutes = 60,
        ),
    )

    val ids: Set<String> = setOf(FOCUS, MORNING, EVENING, WORK)

    fun factory(id: String): Routine? = all.firstOrNull { it.id == id }
}

/**
 * Keeping one emoji out of whatever a keyboard hands over. There is no grapheme API in common
 * Kotlin, so this walks code points and keeps the pieces that belong to a single emoji together:
 * skin tones, variation selectors, keycaps, tag sequences, ZWJ families and the two regional
 * indicators of a flag. Anything that does not start like an emoji — a letter, a digit on its
 * own, punctuation — comes back empty rather than as a stray character on the card.
 */
object Emoji {
    private const val ZWJ = 0x200D
    private const val VS16 = 0xFE0F
    private const val VS15 = 0xFE0E
    private const val KEYCAP = 0x20E3
    private val SKIN = 0x1F3FB..0x1F3FF
    private val REGIONAL = 0x1F1E6..0x1F1FF
    private val TAGS = 0xE0020..0xE007F

    /** A safety net, not a spec: the scanner already bounds the cluster structurally. */
    private const val MAX_CHARS = 40

    private fun isBase(cp: Int): Boolean = cp >= 0x1F000 ||
        cp in 0x2100..0x21FF || cp in 0x2300..0x23FF || cp in 0x25A0..0x25FF ||
        cp in 0x2600..0x27BF || cp in 0x2900..0x297F || cp in 0x2B00..0x2BFF ||
        cp == 0x00A9 || cp == 0x00AE || cp == 0x3030 || cp == 0x303D

    private fun isKeycapBase(cp: Int) = cp in '0'.code..'9'.code || cp == '#'.code || cp == '*'.code

    private fun isTrailer(cp: Int) = cp == VS16 || cp == VS15 || cp == KEYCAP || cp in SKIN || cp in TAGS

    /** The first emoji in [raw], with everything that belongs to it and nothing that does not. */
    fun firstCluster(raw: String): String {
        val cps = raw.trim().toCodePointArray()
        if (cps.isEmpty()) return ""
        val first = cps[0]
        // "1️⃣" starts with an ASCII digit and is still one emoji; a bare "1" is not.
        val keycap = isKeycapBase(first) && cps.drop(1).take(2).contains(KEYCAP)
        if (!keycap && !isBase(first)) return ""
        val out = StringBuilder()
        out.appendCp(first)
        var i = 1
        var flagOpen = first in REGIONAL
        while (i < cps.size) {
            val cp = cps[i]
            when {
                isTrailer(cp) -> { out.appendCp(cp); i++ }
                // A family or a profession: the joiner and whatever it joins belong to this one.
                cp == ZWJ && i + 1 < cps.size && isBase(cps[i + 1]) -> {
                    out.appendCp(cp); out.appendCp(cps[i + 1]); i += 2
                    flagOpen = false
                }
                flagOpen && cp in REGIONAL -> { out.appendCp(cp); flagOpen = false; i++ }
                else -> break
            }
        }
        return out.toString().take(MAX_CHARS)
    }

    // Named away from CharSequence.codePoints(), which exists on the JVM and returns a stream.
    private fun String.toCodePointArray(): IntArray {
        val out = ArrayList<Int>(length)
        var i = 0
        while (i < length) {
            val c = this[i]
            if (c.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) {
                out.add(0x10000 + ((c.code - 0xD800) shl 10) + (this[i + 1].code - 0xDC00))
                i += 2
            } else {
                out.add(c.code)
                i++
            }
        }
        return out.toIntArray()
    }

    private fun StringBuilder.appendCp(cp: Int) {
        if (cp <= 0xFFFF) {
            append(cp.toChar())
        } else {
            val v = cp - 0x10000
            append((0xD800 + (v shr 10)).toChar())
            append((0xDC00 + (v and 0x3FF)).toChar())
        }
    }

    /** A starter set for the picker, so nobody has to go hunting on the keyboard. */
    val suggestions: List<String> = listOf(
        "🎯", "🌅", "🌙", "💼", "📚", "🏃", "🧘", "🍽",
        "💤", "✍️", "🎧", "🧠", "☕️", "🛠", "🙏", "🌿",
        "🔒", "📵", "⛰", "🏡", "👨‍👩‍👧", "🚗", "✈️", "🎬",
    )
}
