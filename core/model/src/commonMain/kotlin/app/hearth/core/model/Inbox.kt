package app.hearth.core.model

import kotlinx.serialization.Serializable

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

/**
 * Something said or typed that hasn't got its proper place yet. Nothing a person says is ever
 * lost: if Hearth can't act on it (offline, or the section isn't built yet) it lands here,
 * labelled with the section it most likely belongs to.
 */
@Serializable
data class InboxItem(
    val id: String,
    val text: String,
    val section: Section,
    val archived: Boolean = false,
    val createdAt: Long,
    val addedById: String? = null,
)
