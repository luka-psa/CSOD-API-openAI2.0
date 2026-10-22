package app.hearth.core.model

/** Languages the UI and the local parser support. Flemish Dutch is the default. */
enum class Lang {
    NL, EN;

    companion object {
        fun fromTag(tag: String?): Lang = if (tag?.lowercase()?.startsWith("en") == true) EN else NL
    }
}
