package ie.stu.invoker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.stu.invoker.ui.theme.Theme

enum class ButtonVariant { Filled, Tonal, Outlined, Text }
enum class ButtonSize { Md, Lg, Xl }

/**
 * Buttons styled after the design's `.btn` rules. Material 3's stock Buttons assume the M3 theme;
 * we paint our own surface so the dark glass aesthetic stays consistent across the app.
 */
/**
 * Standard app button. Pass [widthAnchor] when the visible label can change across states
 * (e.g. "Change" / "Done"): the anchor is rendered invisibly *underneath* the active label so
 * the button reserves a stable width across renders. Saves us from hard-coding pixel widths and
 * keeps things readable when a future locale resource swaps the text.
 */
@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    variant: ButtonVariant = ButtonVariant.Tonal,
    size: ButtonSize = ButtonSize.Md,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tone: ChipTone? = null,
    widthAnchor: String? = null,
) {
    val (bg, fg, border) = buttonColors(variant, tone)
    val (padH, padV, font, radius, weight) = buttonMetrics(size)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(radius.dp))
            .let { if (border != null) it.border(1.dp, border, RoundedCornerShape(radius.dp)) else it }
            .background(bg)
            .alpha(if (enabled) 1f else 0.42f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = padH.dp, vertical = padV.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        StableWidthLabel(label = label, anchor = widthAnchor, color = fg, fontSize = font.sp, fontWeight = weight)
    }
}

@Composable
private fun StableWidthLabel(
    label: String,
    anchor: String?,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
) {
    if (anchor == null || anchor == label) {
        Text(label, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, softWrap = false)
        return
    }
    Box(contentAlignment = Alignment.Center) {
        // Invisible anchor reserves the width — measured but never drawn.
        Text(
            anchor,
            color = Color.Transparent,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
        )
        Text(label, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, softWrap = false)
    }
}

private fun buttonColors(variant: ButtonVariant, tone: ChipTone?): Triple<Color, Color, Color?> {
    val tonedFill = when (tone) {
        ChipTone.Ok -> Theme.StatusOk
        ChipTone.Warn -> Theme.StatusWarn
        ChipTone.Err -> Theme.StatusErr
        else -> null
    }
    return when (variant) {
        ButtonVariant.Filled -> Triple(tonedFill ?: Theme.Primary, Theme.OnPrimary, null)
        ButtonVariant.Tonal -> Triple(Color.White.copy(alpha = 0.06f), Theme.Fg1, Theme.Line2)
        ButtonVariant.Outlined -> Triple(Color.Transparent, Theme.Fg1, Theme.Line3)
        ButtonVariant.Text -> Triple(Color.Transparent, Theme.Fg2, null)
    }
}

private data class ButtonMetrics(val padH: Int, val padV: Int, val font: Int, val radius: Int, val weight: FontWeight)

private fun buttonMetrics(size: ButtonSize): ButtonMetrics = when (size) {
    ButtonSize.Md -> ButtonMetrics(padH = 18, padV = 11, font = 14, radius = Theme.RadiusBtn, weight = FontWeight.Medium)
    ButtonSize.Lg -> ButtonMetrics(padH = 22, padV = 14, font = 15, radius = 12, weight = FontWeight.Medium)
    ButtonSize.Xl -> ButtonMetrics(padH = 28, padV = 16, font = 16, radius = 12, weight = FontWeight.SemiBold)
}
