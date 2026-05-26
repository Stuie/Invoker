package ie.stu.invoker.ui.controls

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ie.stu.invoker.ui.clickableNoRipple
import ie.stu.invoker.ui.theme.Theme

@Composable
fun AppSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val knobOffset by animateDpAsState(if (on) 20.dp else 3.dp, animationSpec = tween(160))
    val knobSize by animateDpAsState(if (on) 18.dp else 16.dp, animationSpec = tween(160))
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(if (on) Theme.Primary else Color.White.copy(alpha = 0.08f))
            .border(width = 1.dp, color = if (on) Color.Transparent else Theme.Line2, shape = CircleShape)
            .clickableNoRipple { onChange(!on) },
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .align(Alignment.CenterStart)
                .size(knobSize)
                .clip(CircleShape)
                .background(if (on) Theme.OnPrimary else Theme.Fg2),
        )
    }
}
