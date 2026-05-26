package ie.stu.invoker.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Palette tokens lifted from the Claude Design handoff (`styles.css`). The CSS uses oklch — we
 * convert once at object load so the rest of the codebase reads natural Compose Color constants.
 */
object Theme {
    // Ground / surface
    val Ground0 = Color(0xFF07090D)
    val Ground1 = Color(0xFF0B0E14)
    val Ground2 = Color(0xFF11151C)

    val Surface1 = Color(0xFF0F1218).copy(alpha = 0.55f)
    val Surface2 = Color(0xFF161A21).copy(alpha = 0.72f)
    val Surface3 = Color(0xFF1E232C).copy(alpha = 0.85f)

    // Foreground
    val Fg1 = Color(0xFFEEF0F4)
    val Fg2 = Color(0xFFB6BCC7)
    val Fg3 = Color(0xFF7A8290)
    val Fg4 = Color(0xFF4D5562)

    // Hairlines
    val Line1 = Color.White.copy(alpha = 0.07f)
    val Line2 = Color.White.copy(alpha = 0.12f)
    val Line3 = Color.White.copy(alpha = 0.20f)

    // Status (computed from oklch so they exactly match the design)
    val StatusReady = oklch(0.72, 0.10, 245.0)
    val StatusReadyBg = oklch(0.32, 0.07, 245.0, alpha = 0.30f)
    val StatusOk = oklch(0.78, 0.12, 150.0)
    val StatusOkBg = oklch(0.32, 0.08, 150.0, alpha = 0.30f)
    val StatusOkText = oklch(0.92, 0.05, 150.0)
    val StatusWarn = oklch(0.82, 0.13, 80.0)
    val StatusWarnBg = oklch(0.36, 0.09, 80.0, alpha = 0.32f)
    val StatusWarnText = oklch(0.94, 0.06, 80.0)
    val StatusErr = oklch(0.72, 0.14, 22.0)
    val StatusErrBg = oklch(0.33, 0.10, 22.0, alpha = 0.32f)
    val StatusErrText = oklch(0.92, 0.05, 22.0)

    val Primary = oklch(0.74, 0.11, 245.0)
    val PrimaryHover = oklch(0.78, 0.11, 245.0)
    val OnPrimary = Color(0xFF0A0D12)

    // Brand glyph gradient (the "IV" tile in nav rail + About)
    val BrandGradTop = oklch(0.55, 0.14, 280.0)
    val BrandGradBottom = oklch(0.40, 0.12, 245.0)

    // MTG mana backdrop preset. Blob colors are pulled from land-card hues:
    // blue #0E68AB, green #00733E, red #D3262A. White and black aren't viable as
    // light sources; the near-black ground carries the "black mana" presence. Each
    // blob lerps between two stops on independent timing so the field melts through
    // the three transitions (blue↔green, red↔blue, green↔red).
    val ArtGround = Color(0xFF060810)
    val ArtA1 = Color(0xFF0E68AB); val ArtA2 = Color(0xFF00733E)   // blue → green
    val ArtB1 = Color(0xFFD3262A); val ArtB2 = Color(0xFF0E68AB)   // red  → blue
    val ArtC1 = Color(0xFF00733E); val ArtC2 = Color(0xFFD3262A)   // green → red

    val RadiusCard = 14
    val RadiusBtn = 10
    val RadiusChip = 999
}

/** oklch(L C H) → sRGB Color. L in 0..1, C in 0..~0.4, H in degrees. */
private fun oklch(l: Double, c: Double, h: Double, alpha: Float = 1f): Color {
    val hRad = Math.toRadians(h)
    val a = c * cos(hRad)
    val b = c * sin(hRad)

    // oklab → linear LMS
    val lp = l + 0.3963377774 * a + 0.2158037573 * b
    val mp = l - 0.1055613458 * a - 0.0638541728 * b
    val sp = l - 0.0894841775 * a - 1.2914855480 * b
    val lc = lp * lp * lp
    val mc = mp * mp * mp
    val sc = sp * sp * sp

    // LMS → linear sRGB
    var r = 4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc
    var g = -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc
    var bl = -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc

    // Linear sRGB → sRGB (gamma)
    r = toSrgbGamma(r); g = toSrgbGamma(g); bl = toSrgbGamma(bl)
    return Color(
        red = r.toFloat().coerceIn(0f, 1f),
        green = g.toFloat().coerceIn(0f, 1f),
        blue = bl.toFloat().coerceIn(0f, 1f),
        alpha = alpha,
    )
}

private fun toSrgbGamma(linear: Double): Double {
    val clamped = linear.coerceIn(0.0, 1.0)
    return if (clamped <= 0.0031308) 12.92 * clamped
    else 1.055 * clamped.pow(1.0 / 2.4) - 0.055
}
