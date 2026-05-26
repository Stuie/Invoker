package ie.stu.invoker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.stu.invoker.ui.theme.Theme

@Composable
fun UpdateBanner(
    heading: String,
    subheading: String,
    icon: ImageVector,
    tone: ChipTone,
    actionLabel: String,
    onAction: () -> Unit,
    actionVariant: ButtonVariant = ButtonVariant.Filled,
) {
    val (top, bottom, border, iconTint) = bannerColors(tone)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(heading, color = Theme.Fg1, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(subheading, color = Theme.Fg2, fontSize = 12.5.sp)
        }
        Spacer(Modifier.width(14.dp))
        AppButton(
            label = actionLabel,
            onClick = onAction,
            variant = actionVariant,
            tone = tone,
            icon = null,
        )
    }
}

private data class BannerColors(val top: Color, val bottom: Color, val border: Color, val iconTint: Color)

private fun bannerColors(tone: ChipTone): BannerColors = when (tone) {
    ChipTone.Warn -> BannerColors(
        top = Theme.StatusWarnBg.copy(alpha = 0.18f),
        bottom = Theme.StatusWarnBg.copy(alpha = 0.06f),
        border = Theme.StatusWarn.copy(alpha = 0.32f),
        iconTint = Theme.StatusWarn,
    )
    ChipTone.Err -> BannerColors(
        top = Theme.StatusErrBg.copy(alpha = 0.18f),
        bottom = Theme.StatusErrBg.copy(alpha = 0.06f),
        border = Theme.StatusErr.copy(alpha = 0.32f),
        iconTint = Theme.StatusErr,
    )
    else -> BannerColors(
        top = Color.White.copy(alpha = 0.06f),
        bottom = Color.White.copy(alpha = 0.02f),
        border = Theme.Line2,
        iconTint = Theme.Fg2,
    )
}

private operator fun BannerColors.component1() = top
private operator fun BannerColors.component2() = bottom
private operator fun BannerColors.component3() = border
private operator fun BannerColors.component4() = iconTint
