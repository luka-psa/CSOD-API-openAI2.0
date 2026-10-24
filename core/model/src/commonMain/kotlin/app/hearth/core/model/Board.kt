package app.hearth.core.model

import kotlinx.serialization.Serializable

// The Werkbord: what we agree on to make the household run better.

/** Someone in the household. Goals and frictions can belong to a person. */
@Serializable
data class Member(val id: String, val name: String, val color: Int, val createdAt: Long)

enum class BoardKind {
    /** Huisregel: an agreement ("schoenen in de kast", "wie kookt, wast niet af"). */
    RULE,
    /** Frictie: something noticed and not liked; a working point for the weekoverleg. */
    FRICTION,
    /** Doel: something to achieve, personal or together, with one small next step. */
    GOAL,
    /**
     * Idee: a way to make it easier or quicker ("de was op zondag, dan is maandag rustig"). Talked about
     * like a friction: it becomes a house rule, a change, or we let it go.
     */
    IDEA,
}

enum class BoardStatus {
    /** Rules in force, frictions still to talk about, goals in progress. */
    OPEN,
    /** A friction that became a house rule. */
    BECAME_RULE,
    /** A friction that became a change: a project or task on the backlog. */
    BECAME_CHANGE,
    /** A friction we decided to let go. That's a fine outcome too. */
    LET_GO,
    /** A goal reached. */
    ACHIEVED,
}

/** Whose goal: the family, the two of us, or one person ([BoardItem.memberId]). */
enum class GoalScope { FAMILY, COUPLE, PERSONAL }

@Serializable
data class BoardItem(
    val id: String,
    val kind: BoardKind,
    val text: String,
    val status: BoardStatus = BoardStatus.OPEN,
    /** Goal: who it belongs to. Friction: who noticed it. */
    val memberId: String? = null,
    val scope: GoalScope? = null,
    /** Goal: one small next step. */
    val nextStep: String? = null,
    /** What a friction turned into (a rule or a backlog item), or a goal's step on the backlog. */
    val linkedId: String? = null,
    val createdAt: Long,
    val closedAt: Long? = null,
) {
    val isOpen: Boolean get() = status == BoardStatus.OPEN
    /** Frictions and ideas are both working points for the weekoverleg. */
    val isWorkingPoint: Boolean get() = kind == BoardKind.FRICTION || kind == BoardKind.IDEA
}

/** Strips "huisregel:", "doel:", "rule:" and the like from what someone said. */
fun stripBoardPrefix(text: String): String =
    text.trim().replace(Regex("^(?:huisregel|regel|afspraak|doel|frictie|ergernis|idee|verbetering|house rule|rule|goal|friction|idea)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
        .trim().replaceFirstChar { it.uppercase() }

/** A calm colour per person, picked in turn. */
val MEMBER_COLORS = listOf(0xFF3F6FB0, 0xFFC0582A, 0xFF4F7A5E, 0xFF7A5BB0, 0xFFB0457A, 0xFF2F8A86)
