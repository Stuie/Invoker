package ie.stu.invoker.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.stu.invoker.ui.theme.Theme

enum class ChipTone { Ready, Ok, Warn, Err, Busy, Neutral }

@Composable
fun StatusChip(tone: ChipTone, label: String) {
    val (dot, halo, text) = chipColors(tone)
    val alphaAnim by rememberInfiniteTransition().animateFloat(
        initialValue = if (tone == ChipTone.Busy) 0.45f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Theme.Line2, CircleShape)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(dot, halo, if (tone == ChipTone.Busy) alphaAnim else 1f)
        Spacer(Modifier.width(8.dp))
        Text(label, color = text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Dot(color: Color, halo: Color, alpha: Float) {
    Box(
        modifier = Modifier
            .size(14.dp) // hit box that holds dot + halo
            .drawBehind {
                drawCircle(halo, radius = size.minDimension / 2f, center = Offset(size.width / 2, size.height / 2))
                drawCircle(
                    color.copy(alpha = alpha),
                    radius = 4.dp.toPx(),
                    center = Offset(size.width / 2, size.height / 2),
                )
            }
    )
}

private fun chipColors(tone: ChipTone): Triple<Color, Color, Color> = when (tone) {
    ChipTone.Ready -> Triple(Theme.StatusReady, Theme.StatusReadyBg, Theme.Fg2)
    ChipTone.Ok -> Triple(Theme.StatusOk, Theme.StatusOkBg, Theme.StatusOkText)
    ChipTone.Warn -> Triple(Theme.StatusWarn, Theme.StatusWarnBg, Theme.StatusWarnText)
    ChipTone.Err -> Triple(Theme.StatusErr, Theme.StatusErrBg, Theme.StatusErrText)
    ChipTone.Busy -> Triple(Theme.StatusReady, Theme.StatusReadyBg, Theme.Fg2)
    ChipTone.Neutral -> Triple(Theme.Fg3, Color.White.copy(alpha = 0.04f), Theme.Fg2)
}
