package app.hearth.ui.theme

import androidx.compose.ui.graphics.Color
import app.hearth.core.model.Section

/** A calm colour pair per section, so the eye finds "the appointments" without reading. */
data class SectionStyle(val ink: Color, val tint: Color)

fun Section.style(): SectionStyle = when (this) {
    Section.SHOPPING, Section.STOCK -> SectionStyle(Color(0xFF4F7A5E), Color(0xFFE3EFE6))
    Section.APPOINTMENT -> SectionStyle(Color(0xFF3F6FB0), Color(0xFFE2ECF8))
    Section.ADMIN -> SectionStyle(Color(0xFF7A5BB0), Color(0xFFECE6F7))
    Section.PROJECT -> SectionStyle(Color(0xFFC0582A), Color(0xFFFBE7DC))
    Section.CHORE -> SectionStyle(Color(0xFF2F8A86), Color(0xFFDDF1EF))
    Section.RHYTHM, Section.HABIT -> SectionStyle(Color(0xFFB08A1E), Color(0xFFF7EFD6))
    Section.PROTECTED_TIME -> SectionStyle(Color(0xFFB0457A), Color(0xFFF8E3EE))
    Section.FRICTION -> SectionStyle(Color(0xFFA14B3B), Color(0xFFF6E2DD))
    Section.NOTE -> SectionStyle(Color(0xFF5F6B7A), Color(0xFFE7EAEE))
    Section.OTHER -> SectionStyle(Color(0xFF8A8079), Color(0xFFEFEBE6))
}
