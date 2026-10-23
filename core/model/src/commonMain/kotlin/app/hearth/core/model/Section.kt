package app.hearth.core.model

/** The three ways open work is planned, plus a tray for what isn't sorted yet. */
enum class TaskKind {
    /** Ritme & gewoontes: comes back every now and then, never planned, never "late". */
    ROUTINE,
    /** Admin: small things with a deadline or urgency; can wait for a reply. */
    ADMIN,
    /** Projecten: bigger things with steps, prep and a time slot. */
    PROJECT,
    /** Nog te sorteren: notes, frictions, rules and anything unclear, until those get their own home. */
    SORT,
}

val Section.kind: TaskKind
    get() = when (this) {
        Section.CHORE, Section.RHYTHM, Section.HABIT -> TaskKind.ROUTINE
        Section.ADMIN, Section.APPOINTMENT -> TaskKind.ADMIN
        Section.PROJECT -> TaskKind.PROJECT
        else -> TaskKind.SORT
    }

/** Sections that live on the Werkbord rather than the backlog. */
val Section.boardKind: BoardKind?
    get() = when (this) {
        Section.NOTE -> BoardKind.RULE
        Section.FRICTION -> BoardKind.FRICTION
        Section.GOAL -> BoardKind.GOAL
        else -> null
    }

/** Where something belongs in Hearth. The router sorts every message into one of these. */
enum class Section {
    SHOPPING, STOCK, APPOINTMENT, ADMIN, PROJECT, CHORE, RHYTHM, HABIT, PROTECTED_TIME, FRICTION, NOTE, GOAL, OTHER;

    fun label(lang: Lang): String = when (lang) {
        Lang.NL -> when (this) {
            SHOPPING -> "boodschappen"
            STOCK -> "voorraad"
            APPOINTMENT -> "afspraak"
            ADMIN -> "administratie"
            PROJECT -> "project"
            CHORE -> "huishoudtaak"
            RHYTHM -> "ritme"
            HABIT -> "gewoonte"
            PROTECTED_TIME -> "vrije tijd"
            FRICTION -> "ergernis"
            NOTE -> "huisregel"
            GOAL -> "doel"
            OTHER -> "nog te sorteren"
        }
        Lang.EN -> when (this) {
            SHOPPING -> "shopping"
            STOCK -> "stock"
            APPOINTMENT -> "appointment"
            ADMIN -> "admin"
            PROJECT -> "project"
            CHORE -> "chore"
            RHYTHM -> "rhythm"
            HABIT -> "habit"
            PROTECTED_TIME -> "protected time"
            FRICTION -> "friction"
            NOTE -> "house rule"
            GOAL -> "goal"
            OTHER -> "to sort"
        }
    }
}
