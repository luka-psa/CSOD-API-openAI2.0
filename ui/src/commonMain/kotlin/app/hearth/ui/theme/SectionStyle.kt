package app.hearth.ui.theme

import androidx.compose.ui.graphics.Color
import app.hearth.core.model.Section
import app.hearth.core.model.Topic
import app.hearth.core.model.topic

/** A calm colour pair: [ink] for icons and labels, [tint] for the soft background behind them. */
data class SectionStyle(val ink: Color, val tint: Color)

/**
 * One colour per topic, the same on every screen (Vandaag, Taken, Weekoverleg, Werkbord), so the eye finds
 * "the paperwork" without reading. Never red: friction is a muted brick.
 */
fun Topic.style(): SectionStyle = when (this) {
    Topic.FREE_TIME -> SectionStyle(Color(0xFFB0457A), Color(0xFFF8E3EE))
    Topic.GOALS -> SectionStyle(Color(0xFF6B7A1F), Color(0xFFEEF1DA))
    Topic.ADMIN -> SectionStyle(Color(0xFF7A5BB0), Color(0xFFECE6F7))
    Topic.PROJECTS -> SectionStyle(Color(0xFFC0582A), Color(0xFFFBE7DC))
    Topic.HOME -> SectionStyle(Color(0xFF2F8A86), Color(0xFFDDF1EF))
    Topic.RHYTHM -> SectionStyle(Color(0xFFB08A1E), Color(0xFFF7EFD6))
    Topic.SHOPPING -> SectionStyle(Color(0xFF4F7A5E), Color(0xFFE3EFE6))
    Topic.MENU -> SectionStyle(Color(0xFF3F6FB0), Color(0xFFE2ECF8))
    Topic.FRICTION -> SectionStyle(Color(0xFFA14B3B), Color(0xFFF6E2DD))
    Topic.RULES -> SectionStyle(Color(0xFF5F6B7A), Color(0xFFE7EAEE))
}

/** What isn't sorted yet: a quiet grey. */
val UnsortedStyle = SectionStyle(Color(0xFF8A8079), Color(0xFFEFEBE6))

/** A section wears the colour of the topic it belongs to. */
fun Section.style(): SectionStyle = topic()?.style() ?: UnsortedStyle

/** Hearth's own voice (Luister mee, a calm note): the ember accent, never a topic's colour. */
val AccentStyle = SectionStyle(HearthColors.Ember, HearthColors.EmberSoft)
