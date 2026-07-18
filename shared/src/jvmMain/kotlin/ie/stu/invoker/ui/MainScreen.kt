package ie.stu.invoker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ie.stu.invoker.ui.theme.Theme

/**
 * @param debugVisible whether the F3 debug overlay is shown. Hoisted to the caller so the F3/Esc
 *   hotkeys can be handled at the [androidx.compose.ui.window.Window] level, which — unlike a
 *   focus-based `onPreviewKeyEvent` — receives key events reliably regardless of which control is
 *   focused (a plain focusable root silently stops getting events once a child grabs focus).
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    debugVisible: Boolean = false,
    onCloseDebug: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().background(Theme.Ground1)) {
            NavRail(state.destination, viewModel::navigateTo)
            Box(Modifier.fillMaxSize().weight(1f)) {
                AtmosphericBackground()
                Scrim()
                Box(Modifier.fillMaxSize()) {
                    when (state.destination) {
                        Destination.Home -> HomePane(viewModel, state)
                        Destination.Settings -> SettingsPane(state, viewModel)
                        Destination.Community -> CommunityPane()
                        Destination.About -> AboutPane(state, viewModel)
                    }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                )
            }
        }

        if (debugVisible) {
            DebugOverlay(state = state, onClose = onCloseDebug)
        }
    }

    state.clients.forEachIndexed { idx, client ->
        if (state.settings.showClientConsole) {
            ConsoleWindow(title = String.format(Strings.CONSOLE_TITLE_CLIENT, idx + 1), process = client)
        }
    }
    state.server?.let { srv ->
        if (state.settings.showServerConsole) {
            ConsoleWindow(title = Strings.CONSOLE_TITLE_SERVER, process = srv)
        }
    }
}

@Composable
private fun NavRail(selected: Destination, onSelect: (Destination) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(88.dp)
            // Fully opaque so the atmospheric backdrop never bleeds through with a hard edge.
            // The design intends a translucent glass + backdrop-filter blur; without
            // backdrop-filter in Compose, opaque is the right approximation.
            .background(Color(0xFF080A0E))
            .border(width = 1.dp, color = Theme.Line1)
            .padding(top = 44.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandGlyph()
        Spacer(Modifier.height(18.dp))
        Destination.entries.forEach { dest ->
            RailItem(dest, dest == selected) { onSelect(dest) }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * The launcher's brand icon. Loads `icon.png` from the runtime classpath (wired in by
 * desktopApp's build script under `sourceSets["main"].resources`). Same source as the
 * Compose window icon and the jpackage installer icon, so all three surfaces stay
 * visually consistent.
 */
@Composable
private fun BrandGlyph(size: Int = 36) {
    Image(
        painter = androidx.compose.ui.res.painterResource("icon.png"),
        contentDescription = Strings.ABOUT_APP_NAME,
        modifier = Modifier.size(size.dp),
    )
}

@Composable
private fun RailItem(dest: Destination, active: Boolean, onClick: () -> Unit) {
    val itemBg = if (active) Color.White.copy(alpha = 0.06f) else Color.Transparent
    val iconBg = if (active) Theme.Surface3 else Color.Transparent
    val labelColor = if (active) Theme.Fg1 else Theme.Fg3

    Box(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(itemBg)
            .clickableNoRipple(onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 26.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(dest.icon, contentDescription = dest.label, tint = labelColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text(
                dest.label,
                color = labelColor,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Brand glyph reused on About (larger), exported so AboutPane can call it. */
@Composable
fun BrandTile(size: Int = 60) = BrandGlyph(size)
