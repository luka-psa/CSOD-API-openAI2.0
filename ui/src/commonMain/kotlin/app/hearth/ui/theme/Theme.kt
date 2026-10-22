package app.hearth.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.jakarta_bold
import app.hearth.ui.resources.jakarta_medium
import app.hearth.ui.resources.jakarta_regular
import app.hearth.ui.resources.jakarta_semibold
import org.jetbrains.compose.resources.Font

/** Hearth's palette: warm paper, soft ink, one ember accent. Calm enough to live on a kitchen wall. */
object HearthColors {
    val Ember = Color(0xFFE0612F)
    val EmberSoft = Color(0xFFFCE7DD)
    val Canvas = Color(0xFFF6F2EC)
    val Card = Color(0xFFFFFFFF)
    val CardMuted = Color(0xFFF9F6F1)
    val Ink = Color(0xFF231E1A)
    val InkSoft = Color(0xFF6E655D)
    val InkFaint = Color(0xFFA39A92)
    val Line = Color(0xFFEAE3DA)
    val Sage = Color(0xFF4F7A5E)
    val SageSoft = Color(0xFFE3EFE6)
}

private val Colors = lightColorScheme(
    primary = HearthColors.Ember,
    onPrimary = Color.White,
    primaryContainer = HearthColors.EmberSoft,
    onPrimaryContainer = Color(0xFF7A2E10),
    secondary = HearthColors.Sage,
    onSecondary = Color.White,
    secondaryContainer = HearthColors.SageSoft,
    onSecondaryContainer = Color(0xFF1F3F2A),
    background = HearthColors.Canvas,
    onBackground = HearthColors.Ink,
    surface = HearthColors.Card,
    onSurface = HearthColors.Ink,
    surfaceVariant = HearthColors.CardMuted,
    onSurfaceVariant = HearthColors.InkSoft,
    surfaceContainerLowest = HearthColors.Card,
    surfaceContainerLow = HearthColors.CardMuted,
    surfaceContainer = HearthColors.CardMuted,
    surfaceContainerHigh = HearthColors.Card,
    surfaceContainerHighest = HearthColors.Card,
    outline = HearthColors.Line,
    outlineVariant = HearthColors.Line,
    error = Color(0xFFB3261E),
)

private val HearthShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
private fun jakarta() = FontFamily(
    Font(Res.font.jakarta_regular, FontWeight.Normal),
    Font(Res.font.jakarta_medium, FontWeight.Medium),
    Font(Res.font.jakarta_semibold, FontWeight.SemiBold),
    Font(Res.font.jakarta_bold, FontWeight.Bold),
)

// Large, airy type: the tablet is read from across the kitchen.
@Composable
private fun typography(): Typography {
    val f = jakarta()
    fun s(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) =
        TextStyle(fontFamily = f, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = spacing.sp)
    return Typography(
        displaySmall = s(40, FontWeight.Bold, 46, -0.8),
        headlineLarge = s(34, FontWeight.Bold, 40, -0.6),
        headlineMedium = s(28, FontWeight.Bold, 34, -0.4),
        headlineSmall = s(22, FontWeight.SemiBold, 28, -0.2),
        titleLarge = s(20, FontWeight.SemiBold, 26, -0.1),
        titleMedium = s(17, FontWeight.SemiBold, 23),
        titleSmall = s(15, FontWeight.SemiBold, 20),
        bodyLarge = s(18, FontWeight.Normal, 25),
        bodyMedium = s(16, FontWeight.Normal, 22),
        bodySmall = s(14, FontWeight.Normal, 19),
        labelLarge = s(16, FontWeight.SemiBold, 20),
        labelMedium = s(14, FontWeight.Medium, 18),
        labelSmall = s(12, FontWeight.SemiBold, 16, 0.2),
    )
}

@Composable
fun HearthTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = typography(), shapes = HearthShapes, content = content)
}
