package ie.stu.invoker.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.stu.invoker.ui.theme.Theme

@Composable
fun AppTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
    width: Dp = 220.dp,
    mono: Boolean = true,
    singleLine: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onChange,
        interactionSource = interaction,
        cursorBrush = SolidColor(Theme.Primary),
        textStyle = TextStyle(
            color = Theme.Fg1,
            fontSize = 13.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        ),
        singleLine = singleLine,
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.04f))
            .border(
                1.dp,
                if (focused) Theme.Primary else Theme.Line2,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        decorationBox = { inner ->
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, color = Theme.Fg4, fontSize = 13.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default)
            }
            inner()
        },
    )
}
