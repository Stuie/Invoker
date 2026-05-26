package ie.stu.invoker.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.stu.invoker.ui.clickableNoRipple
import ie.stu.invoker.ui.theme.Theme

data class SegmentOption<T>(val value: T, val label: String)

@Composable
fun <T> Segment(options: List<SegmentOption<T>>, value: T, onChange: (T) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Theme.Line2, RoundedCornerShape(10.dp))
            .padding(3.dp),
    ) {
        options.forEach { opt ->
            val selected = opt.value == value
            val bg = if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent
            val fg = if (selected) Theme.Fg1 else Theme.Fg2
            Text(
                opt.label,
                color = fg,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(bg)
                    .clickableNoRipple { onChange(opt.value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
