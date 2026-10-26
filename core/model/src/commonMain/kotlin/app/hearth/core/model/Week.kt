package app.hearth.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

// The Weekoverleg: a short guided meeting, once a week.

/** One weekly meeting that was held, with the summary written when it ended (kept). */
@Serializable
data class Huddle(val id: String, val heldOn: String, val createdAt: Long, val summary: String = "") {
    val date: LocalDate? get() = runCatching { LocalDate.parse(heldOn) }.getOrNull()
}

/**
 * The household's topics, one name, icon and colour each, the same on every screen: a tile in the
 * weekoverleg (Luister mee lights up the one a sentence lands in), a panel in Taken, a column on the
 * Werkbord, a card on Vandaag. So the whole household fits on one screen and nobody has to hold it in their head.
 * Always in this order, in three groups ([group]): the tasks, then food, then the Werkbord.
 */
enum class Topic { ADMIN, PROJECTS, HOME, RHYTHM, FREE_TIME, MENU, SHOPPING, RULES, FRICTION, GOALS }

/** The three kinds of topics, side by side on every screen: what there is to do, what we eat, what we agree on. */
enum class TopicGroup { TASKS, FOOD, BOARD }

val Topic.group: TopicGroup get() = when (this) {
    Topic.ADMIN, Topic.PROJECTS, Topic.HOME, Topic.RHYTHM, Topic.FREE_TIME -> TopicGroup.TASKS
    Topic.MENU, Topic.SHOPPING -> TopicGroup.FOOD
    Topic.RULES, Topic.FRICTION, Topic.GOALS -> TopicGroup.BOARD
}

fun TopicGroup.title(lang: Lang): String = when (this) {
    TopicGroup.TASKS -> if (lang == Lang.NL) "Taken" else "Tasks"
    TopicGroup.FOOD -> if (lang == Lang.NL) "Eten" else "Food"
    TopicGroup.BOARD -> if (lang == Lang.NL) "Werkbord" else "Board"
}

val TopicGroup.topics: List<Topic> get() = Topic.entries.filter { it.group == this }

fun Topic.title(lang: Lang): String = when (this) {
    Topic.FREE_TIME -> if (lang == Lang.NL) "Vrije tijd" else "Free time"
    Topic.GOALS -> if (lang == Lang.NL) "Doelen" else "Goals"
    Topic.ADMIN -> if (lang == Lang.NL) "Admin & afspraken" else "Admin & appointments"
    Topic.PROJECTS -> if (lang == Lang.NL) "Grote taken" else "Big tasks"
    Topic.HOME -> if (lang == Lang.NL) "Huishouden" else "Household"
    Topic.RHYTHM -> if (lang == Lang.NL) "Ritmes & routines" else "Rhythms & routines"
    Topic.SHOPPING -> if (lang == Lang.NL) "Boodschappen" else "Shopping"
    Topic.MENU -> if (lang == Lang.NL) "Weekmenu" else "Week menu"
    Topic.FRICTION -> if (lang == Lang.NL) "Frictie & ideeën" else "Frictions & ideas"
    Topic.RULES -> if (lang == Lang.NL) "Huisregels" else "House rules"
}

/** Where a section belongs in the weekoverleg; null for what still has to be sorted. */
fun Section.topic(): Topic? = when (this) {
    Section.PROTECTED_TIME -> Topic.FREE_TIME
    Section.ADMIN, Section.APPOINTMENT -> Topic.ADMIN
    Section.PROJECT -> Topic.PROJECTS
    Section.CHORE -> Topic.HOME
    Section.RHYTHM, Section.HABIT -> Topic.RHYTHM
    Section.SHOPPING, Section.STOCK -> Topic.SHOPPING
    Section.NOTE -> Topic.RULES
    Section.FRICTION, Section.IDEA -> Topic.FRICTION
    Section.GOAL -> Topic.GOALS
    Section.OTHER -> null
}

/** A chore with a fixed moment ("na het eten afwassen") belongs to the rhythm of the day, not to the household list. */
val BacklogItem.topic: Topic? get() = when {
    // Routine steps show inside their routine, never on their own.
    isStep -> if (section == Section.PROJECT) Topic.PROJECTS else null
    section == Section.CHORE && anchor != null -> Topic.RHYTHM
    else -> section.topic()
}

/** Everything a topic tile looks at. */
class WeekLook(
    val open: List<BacklogItem>,
    val board: List<BoardItem>,
    val mealsPlanned: Int,
    val shoppingOpen: Int,
    val today: LocalDate,
)

/** A topic in a few calm words ("3 open · 1 wacht op antwoord"); never a red counter. */
fun Topic.glance(look: WeekLook, lang: Lang): String {
    val nl = lang == Lang.NL
    fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
    val open = look.open.filter { !it.done }
    val parts: List<String> = when (this) {
        Topic.FREE_TIME -> {
            val soon = open.filter { it.section == Section.PROTECTED_TIME && (it.plan == Plan.NEXT || (it.localDate?.let { d -> d < look.today.plusDays(7) } ?: false)) }
                .sortedBy { it.date ?: "9" }
            if (soon.isEmpty()) listOf(if (nl) "Nog niets gepland" else "Nothing planned yet")
            else listOf(soon.first().let { f -> f.text + (f.localDate?.let { " (" + dayLabel(it, look.today, lang) + ")" } ?: "") }) +
                listOfNotNull((soon.size - 1).takeIf { it > 0 }?.let { "+$it" })
        }
        Topic.GOALS -> {
            val goals = look.board.filter { it.kind == BoardKind.GOAL && it.isOpen }
            val step = goals.firstNotNullOfOrNull { it.nextStep }
            if (goals.isEmpty()) listOf(if (nl) "Nog geen doelen" else "No goals yet")
            else listOfNotNull(
                if (nl) count(goals.size, "doel", "doelen") else count(goals.size, "goal", "goals"),
                step?.let { if (nl) "volgende stap: $it" else "next step: $it" },
            )
        }
        Topic.ADMIN -> {
            val admin = open.filter { it.topic == Topic.ADMIN }
            if (admin.isEmpty()) listOf(if (nl) "Niets open. Fijn." else "Nothing open. Nice.")
            else listOfNotNull(
                "${admin.size} open",
                admin.count { it.urgent }.takeIf { it > 0 }?.let { if (nl) "$it dringend" else "$it urgent" },
                admin.count { it.waiting }.takeIf { it > 0 }?.let { if (nl) "$it wacht op antwoord" else "$it waiting for a reply" },
            )
        }
        Topic.PROJECTS -> {
            val projects = open.filter { it.section == Section.PROJECT && !it.isStep }
            if (projects.isEmpty()) listOf(if (nl) "Niets lopend" else "Nothing running")
            else listOfNotNull(
                if (nl) "${projects.size} lopend" else "${projects.size} running",
                projects.count { it.postponed >= 2 }.takeIf { it > 0 }?.let { if (nl) "$it vraagt aandacht" else "$it ask for attention" },
            )
        }
        Topic.HOME -> {
            val due = dueRoutines(open, look.today).filter { it.topic == Topic.HOME }
            val chores = open.count { it.topic == Topic.HOME }
            when {
                chores == 0 -> listOf(if (nl) "Nog geen huishoudtaken" else "No household tasks yet")
                due.isEmpty() -> listOf(if (nl) "Loopt rustig" else "Running calmly")
                else -> listOf(if (nl) "${due.size} aan de beurt" else "${due.size} due")
            }
        }
        Topic.RHYTHM -> {
            val routines = open.filter { it.topic == Topic.RHYTHM && !it.isStep }
            val hard = routines.count { it.ease == Ease.HARD }
            val moments = routines.mapNotNull { it.anchor }.distinct().size
            if (routines.isEmpty()) listOf(if (nl) "Nog geen ritme" else "No rhythm yet")
            else listOfNotNull(
                count(routines.size, "routine", "routines"),
                moments.takeIf { it > 1 }?.let { if (nl) "over $it momenten" else "across $it moments" },
                hard.takeIf { it > 0 }?.let { if (nl) "$it te zwaar" else "$it too hard" },
            )
        }
        Topic.SHOPPING -> listOf(
            if (look.shoppingOpen == 0) (if (nl) "De lijst is leeg" else "The list is empty")
            else if (nl) "${look.shoppingOpen} op de lijst" else "${look.shoppingOpen} on the list",
        )
        Topic.MENU -> listOf(
            if (look.mealsPlanned == 0) (if (nl) "Nog niets gepland" else "Nothing planned yet")
            else if (nl) "${look.mealsPlanned} van 7 avonden" else "${look.mealsPlanned} of 7 evenings",
        )
        Topic.FRICTION -> {
            val points = look.board.filter { it.isWorkingPoint && it.isOpen }
            // Talked through but not written yet ("huisregel te maken") counts apart from what's still to discuss.
            val chosen = points.count { it.direction != null }
            val open = points.size - chosen
            if (points.isEmpty()) listOf(if (nl) "Niets dat schuurt" else "Nothing rubbing")
            else listOfNotNull(
                open.takeIf { it > 0 }?.let { if (nl) "$it te bespreken" else "$it to talk through" },
                chosen.takeIf { it > 0 }?.let { if (nl) "$it te maken" else "$it to write" },
            )
        }
        Topic.RULES -> {
            val rules = look.board.count { it.kind == BoardKind.RULE && it.isOpen }
            listOf(if (rules == 0) (if (nl) "Nog geen huisregels" else "No house rules yet") else if (nl) count(rules, "huisregel", "huisregels") else count(rules, "house rule", "house rules"))
        }
    }
    return parts.joinToString(" · ")
}

/** Deadlines ("klaar tegen") in the coming week, earlier ones included. */
fun deadlinesThisWeek(items: List<BacklogItem>, today: LocalDate): List<BacklogItem> =
    items.filter { !it.waiting && it.plan == Plan.DAY && it.finishBy && (it.localDate?.let { d -> d < today.plusDays(7) } ?: false) }
        .sortedBy { it.date }

/** Whether it's a good moment to suggest the weekly meeting: none in the last 6 days. */
fun huddleDue(last: Huddle?, today: LocalDate): Boolean {
    val held = last?.date ?: return true
    return today.toEpochDays() - held.toEpochDays() >= 6
}

/** Wat ging goed: a win someone named. Belongs to the next meeting until that one ends. */
@Serializable
data class Win(val id: String, val text: String, val memberId: String?, val huddleId: String?, val createdAt: Long)

/**
 * One thing said during Luister mee (what the recognizer heard between two pauses). The lines of the
 * last meeting stay readable until the next meeting ends; its summary stays for good.
 */
@Serializable
data class TranscriptLine(val id: String, val text: String, val huddleId: String?, val createdAt: Long, val sorted: Boolean = false)

/**
 * How the week went, agreed together for the household (never per person, never about someone), on the same weather
 * scale as the routines and house rules: zon (liep vanzelf), wolk (vroeg moeite), bliksem (was zwaar). Hearth
 * suggests, the family decides; no agreement ([agreed] false) is an answer too and gives no weather.
 */
@Serializable
data class WeekRating(val ease: Ease?, val suggested: Ease?, val agreed: Boolean, val huddleId: String?)

/**
 * How a house rule went this week, on the same scale as the routines: loopt vanzelf, vraagt moeite, te zwaar.
 * Household-level: it never says who. Two meetings in a row "te zwaar" and it comes back as a friction.
 */
@Serializable
data class RuleCheck(val ruleId: String, val ease: Ease, val huddleId: String?) {
    val worked: Boolean get() = ease != Ease.HARD
}

/**
 * Starts at the sun: the small things and good habits are always forgotten. A cloud when frictions pile up or habits
 * ask for attention with little done. Never a bolt: that is for the family to say.
 */
fun weekSuggestion(done: Int, wins: Int, needingAttention: Int, newFrictions: Int, rulesTooHard: Int): Ease =
    if (newFrictions + rulesTooHard >= 2 || (done + wins == 0 && needingAttention > 1)) Ease.EFFORT else Ease.EASY

/** The weather of a step on the scale, for words and screen readers: "zon", "wolk", "bliksem". */
fun Ease.weather(lang: Lang): String = when (this) {
    Ease.EASY -> if (lang == Lang.NL) "zon" else "sun"
    Ease.EFFORT -> if (lang == Lang.NL) "wolk" else "cloud"
    Ease.HARD -> if (lang == Lang.NL) "bliksem" else "lightning"
}

/** How the week went in words; kind on purpose: "liep vanzelf", "vroeg moeite", "was zwaar". */
fun Ease.weekWord(lang: Lang): String = when (this) {
    Ease.EASY -> if (lang == Lang.NL) "liep vanzelf" else "went smoothly"
    Ease.EFFORT -> if (lang == Lang.NL) "vroeg moeite" else "took effort"
    Ease.HARD -> if (lang == Lang.NL) "was zwaar" else "was heavy"
}
