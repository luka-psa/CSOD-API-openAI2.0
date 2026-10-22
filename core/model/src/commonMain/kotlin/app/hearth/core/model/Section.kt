package app.hearth.core.model

/** Where something belongs in Hearth. The router sorts every message into one of these. */
enum class Section {
    SHOPPING, STOCK, APPOINTMENT, ADMIN, PROJECT, CHORE, RHYTHM, HABIT, PROTECTED_TIME, FRICTION, NOTE, OTHER;

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
            OTHER -> "to sort"
        }
    }
}
