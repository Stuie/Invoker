package ie.stu.invoker.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ie.stu.invoker.ui.theme.Theme
import kotlin.math.roundToInt

/**
 * Three large soft "blobs" drifting on independent slow loops over a ground tone, with hue
 * lerping between palette stops. Mirrors the `bg-art` block in `styles.css`, but uses a radial
 * gradient (color → transparent) per blob so there is no hard edge for the blur to fail against.
 */
@Composable
fun AtmosphericBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()

    val drift1 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(38_000, easing = LinearEasing), RepeatMode.Reverse))
    val drift2 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(46_000, easing = LinearEasing), RepeatMode.Reverse))
    val drift3 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(52_000, easing = LinearEasing), RepeatMode.Reverse))
    val drift4 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(43_000, easing = LinearEasing), RepeatMode.Reverse))
    val hue1 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse))
    val hue2 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(31_000, easing = LinearEasing), RepeatMode.Reverse))
    val hue3 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(28_000, easing = LinearEasing), RepeatMode.Reverse))
    val hue4 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Reverse))

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds() // blobs can't escape into the nav rail
            .background(Theme.ArtGround),
    ) {
        // Top-left
        Blob(lerp(Theme.ArtA1, Theme.ArtA2, hue1), Offset(-0.15f, -0.15f), Offset(0.18f, 0.10f), drift1, 1.10f, 0.95f)
        // Middle-to-bottom-right
        Blob(lerp(Theme.ArtB1, Theme.ArtB2, hue2), Offset(0.20f, 0.30f), Offset(-0.10f, 0.18f), drift2, 1.15f, 0.95f)
        // Upper-right
        Blob(lerp(Theme.ArtC1, Theme.ArtC2, hue3), Offset(0.30f, -0.10f), Offset(0.12f, 0.08f), drift3, 0.90f, 0.85f)
        // Bottom-left — closes the cycle with the one pair the three design blobs don't cover (blue↔red).
        Blob(lerp(Theme.ArtA1, Theme.ArtC2, hue4), Offset(-0.10f, 0.40f), Offset(0.16f, 0.10f), drift4, 1.00f, 0.80f)
    }
}

/**
 * A single soft orb. Positions and size are fractions of the parent so the field scales. The
 * orb is a radial gradient (color → transparent), giving it a natural soft falloff before the
 * Skia blur passes over the top.
 */
@Composable
private fun Blob(
    color: Color,
    origin: Offset,
    travel: Offset,
    t: Float,
    sizeFraction: Float,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val w = (constraints.maxWidth * sizeFraction).roundToInt()
                val h = (constraints.maxHeight * sizeFraction).roundToInt()
                val placeable = measurable.measure(
                    constraints.copy(minWidth = w, maxWidth = w, minHeight = h, maxHeight = h)
                )
                val x = ((origin.x + travel.x * t) * constraints.maxWidth).roundToInt()
                val y = ((origin.y + travel.y * t) * constraints.maxHeight).roundToInt()
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(IntOffset(x, y))
                }
            }
            .blur(60.dp)
            .background(
                Brush.radialGradient(
                    0.0f to color.copy(alpha = alpha),
                    0.45f to color.copy(alpha = alpha * 0.55f),
                    1.0f to Color.Transparent,
                )
            )
    )
}

/** Vertical scrim over the background — kept lighter than the design's CSS so colors keep showing through to the bottom of the pane. */
@Composable
fun Scrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0.0f to Color(0xFF07090D).copy(alpha = 0.20f),
                0.5f to Color(0xFF07090D).copy(alpha = 0.35f),
                1.0f to Color(0xFF07090D).copy(alpha = 0.55f),
            )
        )
    )
}
