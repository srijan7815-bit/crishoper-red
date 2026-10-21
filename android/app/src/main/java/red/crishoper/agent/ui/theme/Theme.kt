package red.crishoper.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Tokens mirrored from the design sheet: black/white + one red. */
object Red {
    val accent = Color(0xFFFF3441)
    val accentDim = Color(0xFFB9212B)
    val green = Color(0xFF40D9A0)
    val amber = Color(0xFFFFB340)
    val blue = Color(0xFF7FA5FF)

    val ink = Color(0xFF070708)
    val surface = Color(0xFF101013)
    val surface2 = Color(0xFF16161A)
    val surface3 = Color(0xFF1D1D22)
    val stroke = Color(0x14FFFFFF)
    val stroke2 = Color(0x24FFFFFF)

    val text = Color(0xFFF4F4F6)
    val text2 = Color(0xFFA9A9B2)
    val text3 = Color(0xFF76767F)
    val text4 = Color(0xFF4F4F58)

    val paper = Color(0xFFF5F4F2)
    val paperSurface = Color(0xFFFBFAF9)
    val paperText = Color(0xFF111114)
    val paperText2 = Color(0xFF54545E)
}

data class RedColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val stroke: Color,
    val onBackground: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val isDark: Boolean,
)

val LocalRedColors = staticCompositionLocalOf {
    RedColors(Red.ink, Red.surface, Red.surface2, Red.stroke, Red.text, Red.text2, Red.accent, true)
}

private val dark = darkColorScheme(
    primary = Red.accent,
    onPrimary = Color.White,
    background = Red.ink,
    onBackground = Red.text,
    surface = Red.surface,
    onSurface = Red.text,
    surfaceVariant = Red.surface2,
    onSurfaceVariant = Red.text2,
    outline = Red.stroke2,
    error = Red.accent,
)

private val light = lightColorScheme(
    primary = Color(0xFFE01124),
    onPrimary = Color.White,
    background = Red.paper,
    onBackground = Red.paperText,
    surface = Red.paperSurface,
    onSurface = Red.paperText,
    surfaceVariant = Color(0xFFF1F0EE),
    onSurfaceVariant = Red.paperText2,
    outline = Color(0x1F0C0C10),
    error = Color(0xFFC00C1D),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.8).sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, letterSpacing = (-0.3).sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = (-0.1).sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, letterSpacing = (-0.1).sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, letterSpacing = 1.2.sp),
)

@Composable
fun RedTheme(
    usePaper: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (usePaper) light else dark
    val extras = if (usePaper) {
        RedColors(Red.paper, Red.paperSurface, Color(0xFFF1F0EE), Color(0x1F0C0C10), Red.paperText, Red.paperText2, Color(0xFFE01124), false)
    } else {
        RedColors(Red.ink, Red.surface, Red.surface2, Red.stroke, Red.text, Red.text2, Red.accent, true)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalRedColors provides extras) {
        MaterialTheme(colorScheme = colors, shapes = shapes, typography = typography, content = content)
    }
}
