package red.crishoper.agent.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlin.math.max

/* ─────────────────────────────────────────────────────────────
   MONO — the design language.

   Strictly black and white. Structure comes from hairlines and
   space, never from shadow. One accent exists and it is inverted
   light: a solid slab of ink. Motion is short and physical.

   Nothing is tinted, so nothing competes: the only way to make
   something important is to invert it.
   ───────────────────────────────────────────────────────────── */

@Immutable
data class MonoColors(
    val dark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surfaceHi: Color,
    val surfaceMax: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val solid: Color,
    val inverseInk: Color,
)

val Obsidian = MonoColors(
    dark = true,
    canvas = Color(0xFF000000),
    surface = Color(0xFF0B0B0C),
    surfaceHi = Color(0xFF151517),
    surfaceMax = Color(0xFF212125),
    hairline = Color(0x1FFFFFFF),
    hairlineStrong = Color(0x3DFFFFFF),
    ink = Color(0xFFF7F7F8),
    muted = Color(0xFF9C9CA3),
    faint = Color(0xFF5C5C63),
    solid = Color(0xFFFFFFFF),
    inverseInk = Color(0xFF000000),
)

val Paper = MonoColors(
    dark = false,
    canvas = Color(0xFFFFFFFF),
    surface = Color(0xFFF6F6F5),
    surfaceHi = Color(0xFFEDEDEB),
    surfaceMax = Color(0xFFE0E0DE),
    hairline = Color(0x1F000000),
    hairlineStrong = Color(0x3D000000),
    ink = Color(0xFF08080A),
    muted = Color(0xFF5E5E66),
    faint = Color(0xFF96969E),
    solid = Color(0xFF000000),
    inverseInk = Color(0xFFFFFFFF),
)

/**
 * Phone metrics derived from the actual device, not a guess. A 5" phone, a
 * foldable opened to a tall narrow pane and a 7" tablet each get their own
 * gutter width, display size and dock geometry.
 */
@Immutable
data class MonoMetrics(
    val widthDp: Int,
    val compact: Boolean,
    val tablet: Boolean,
    val tall: Boolean,
    val gutter: Dp,
    val display: TextUnit,
    val titleSize: TextUnit,
    val bodySize: TextUnit,
    val radius: Dp,
    val dockHeight: Dp,
)

val LocalMono = staticCompositionLocalOf { Obsidian }
val LocalMetrics = staticCompositionLocalOf {
    MonoMetrics(400, compact = false, tablet = false, tall = true, gutter = 20.dp, display = 34.sp, titleSize = 22.sp, bodySize = 15.sp, radius = 26.dp, dockHeight = 64.dp)
}

/** Short, quiet haptics. Fired on every deliberate touch, never on scroll. */
class Haptics(private val fire: (Long) -> Unit) {
    fun tick() = fire(4L)
    fun tap() = fire(8L)
    fun heavy() = fire(18L)
}

val LocalHaptics = staticCompositionLocalOf { Haptics {} }

object MonoType {
    val display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1.1).sp,
    )
    val title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.5).sp,
    )
    val heading = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    )
    val bodySm = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
    /** Machine voice. Always uppercase, always tracked out. */
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
    )
    val monoData = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
        lineHeight = 18.sp,
    )
    val monoSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        letterSpacing = 1.1.sp,
    )
}

@Composable
fun RedTheme(paper: Boolean = false, content: @Composable () -> Unit) {
    val c = if (paper) Paper else Obsidian
    val cfg = LocalConfiguration.current
    val width = cfg.screenWidthDp
    val height = cfg.screenHeightDp
    val fontScale = max(1f, cfg.fontScale)

    val metrics = remember(width, height, fontScale) {
        val compact = width < 360
        val tablet = width >= 600
        val tall = height >= 760
        MonoMetrics(
            widthDp = width,
            compact = compact,
            tablet = tablet,
            tall = tall,
            gutter = when {
                tablet -> 40.dp
                compact -> 16.dp
                else -> 20.dp
            },
            // Display type is the one place a large system font would break the
            // layout, so it is damped rather than left to overflow.
            display = (34f / fontScale.coerceAtMost(1.25f)).sp,
            titleSize = (22f / fontScale.coerceAtMost(1.3f)).sp,
            bodySize = 15.sp,
            radius = if (compact) 22.dp else 26.dp,
            dockHeight = 64.dp,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = paper
            controller.isAppearanceLightNavigationBars = paper
        }
    }

    val scheme = if (paper) {
        lightColorScheme(
            background = c.canvas, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink,
            primary = c.solid, onPrimary = c.inverseInk,
            secondary = c.muted, onSecondary = c.ink,
            outline = c.hairlineStrong, error = c.ink, onError = c.inverseInk,
        )
    } else {
        darkColorScheme(
            background = c.canvas, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink,
            primary = c.solid, onPrimary = c.inverseInk,
            secondary = c.muted, onSecondary = c.ink,
            outline = c.hairlineStrong, error = c.ink, onError = c.inverseInk,
        )
    }

    CompositionLocalProvider(
        LocalMono provides c,
        LocalMetrics provides metrics,
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
