package ie.stu.invoker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/** Compose's default ripple looks heavy on tiny rail items; this strips it. */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this.clickable(interactionSource = source, indication = null, onClick = onClick)
}
