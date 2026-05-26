package ie.stu.invoker.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ie.stu.invoker.ui.theme.Theme

@Composable
fun PaneHeader(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            color = Theme.Fg1,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.02).em,
        )
        if (subtitle != null) {
            Spacer(Modifier.width(14.dp))
            Text(subtitle, color = Theme.Fg3, fontSize = 13.sp)
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}
