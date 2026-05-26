package ie.stu.invoker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import invoker.shared.generated.resources.Res
import invoker.shared.generated.resources.label_xmage
import org.jetbrains.compose.resources.painterResource

@Composable
fun xmageLabelPainter(): Painter = painterResource(Res.drawable.label_xmage)
