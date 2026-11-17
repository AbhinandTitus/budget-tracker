package app.kanakku.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kanakku.R

/** The palette: warm ink neutrals, one champagne-gold accent, and reserved status colours. */
object Ink {
    val Bg = Color(0xFF0C0C0B)
    val Surface = Color(0xFF151514)
    val SurfaceHi = Color(0xFF1C1C1A)
    val SurfaceTop = Color(0xFF262623)
    val Line = Color(0xFF262623)
    val LineStrong = Color(0xFF34332F)

    val Text = Color(0xFFF1F0ED)
    val Text2 = Color(0xFFBAB7B0)
    val Text3 = Color(0xFF88867F)
    val Text4 = Color(0xFF5A5853)

    /** Accent for text and controls. */
    val Gold = Color(0xFFE4C78A)
    val OnGold = Color(0xFF1A150E)

    // Chart marks. Validated as a set against the dark surface for contrast and colour-blind separation.
    val MarkSpent = Color(0xFFB88C31)
    val MarkIncome = Color(0xFF1F9A7A)
    val MarkMuted = Color(0xFF5A5853)
    val TrackSpent = Color(0xFF2A2314)

    // Status. Always paired with words, never colour alone.
    val Good = Color(0xFF7ED3A6)
    val Warn = Color(0xFFE77F3E)
    val Bad = Color(0xFFF7857D)
    val BadFill = Color(0xFFE7524F)
    val TrackWarn = Color(0xFF2E1C10)
    val TrackBad = Color(0xFF341816)
}

private fun geist(weight: Int) = Font(
    R.font.geist,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Geist = FontFamily(geist(400), geist(500), geist(600))

/** Tabular figures, for numbers that line up in a column. */
const val TNUM = "tnum"

object Type {
    val Hero = TextStyle(fontFamily = Geist, fontWeight = FontWeight(500), fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-0.035).em, color = Ink.Text)
    val Figure = TextStyle(fontFamily = Geist, fontWeight = FontWeight(500), fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.02).em, color = Ink.Text)
    val Title = TextStyle(fontFamily = Geist, fontWeight = FontWeight(600), fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.015).em, color = Ink.Text)
    val Heading = TextStyle(fontFamily = Geist, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.005).em, color = Ink.Text)
    val Body = TextStyle(fontFamily = Geist, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 22.sp, color = Ink.Text)
    val BodyStrong = Body.copy(fontWeight = FontWeight(500))
    val Label = TextStyle(fontFamily = Geist, fontWeight = FontWeight(500), fontSize = 13.sp, lineHeight = 18.sp, color = Ink.Text2)
    val Caption = TextStyle(fontFamily = Geist, fontWeight = FontWeight(400), fontSize = 12.5.sp, lineHeight = 17.sp, color = Ink.Text3)
    val Overline = TextStyle(fontFamily = Geist, fontWeight = FontWeight(500), fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em, color = Ink.Text3)
    val Amount = BodyStrong.copy(fontFeatureSettings = TNUM)
}

private val scheme = darkColorScheme(
    primary = Ink.Gold,
    onPrimary = Ink.OnGold,
    secondary = Ink.Gold,
    onSecondary = Ink.OnGold,
    background = Ink.Bg,
    onBackground = Ink.Text,
    surface = Ink.Surface,
    onSurface = Ink.Text,
    surfaceVariant = Ink.SurfaceHi,
    onSurfaceVariant = Ink.Text2,
    surfaceContainer = Ink.Surface,
    surfaceContainerLow = Ink.Surface,
    surfaceContainerHigh = Ink.SurfaceHi,
    surfaceContainerHighest = Ink.SurfaceTop,
    outline = Ink.LineStrong,
    outlineVariant = Ink.Line,
    error = Ink.Bad,
    onError = Ink.OnGold,
    scrim = Color.Black,
)

@Composable
fun KanakkuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography.copy(
            bodyLarge = Type.Body, bodyMedium = Type.Body, bodySmall = Type.Caption,
            titleLarge = Type.Title, titleMedium = Type.Heading, labelLarge = Type.Label,
        ),
        content = content,
    )
}
