package ie.stu.invoker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.stu.invoker.process.AppLog
import ie.stu.invoker.process.LogLevel
import ie.stu.invoker.process.LogLine
import ie.stu.invoker.settings.JavaSource
import ie.stu.invoker.ui.theme.Theme

/** A merged, timestamped line for the overlay — launcher diagnostics and XMage process output alike. */
private data class DebugLine(
    val timestampMs: Long,
    val time: String,
    val tag: String,
    val level: LogLevel?,
    val text: String,
)

private const val MAX_LINES = 3000

/**
 * Minecraft-style F3 debug overlay. Renders over the content pane (not a separate OS window, so it's
 * always one keypress away regardless of the Show*Console settings). Merges the launcher [AppLog]
 * stream with the live stdout/stderr of every running XMage process, plus a snapshot of the state
 * most useful when a launch misbehaves.
 */
@Composable
fun DebugOverlay(state: UiState, onClose: () -> Unit) {
    val lines = remember { mutableStateListOf<DebugLine>() }

    // Launcher diagnostics. AppLog replays its buffer on subscribe, so opening the overlay shows history.
    LaunchedEffect(Unit) {
        AppLog.lines.collect { lines.addTrimmed(it.toDebugLine(tag = "app")) }
    }
    // Live server output.
    LaunchedEffect(state.server) {
        val srv = state.server ?: return@LaunchedEffect
        srv.log.lines.collect { lines.addTrimmed(it.toDebugLine(tag = "server")) }
    }
    // Live client output, one collector per client.
    state.clients.forEachIndexed { idx, client ->
        LaunchedEffect(client) {
            client.log.lines.collect { lines.addTrimmed(it.toDebugLine(tag = "client ${idx + 1}")) }
        }
    }

    // Replayed lines arrive per-source, so sort by timestamp for a coherent chronological view.
    val ordered = lines.sortedBy { it.timestampMs }
    val clipboard = LocalClipboardManager.current

    Box(Modifier.fillMaxSize().background(Theme.Ground0.copy(alpha = 0.94f)).padding(20.dp)) {
        Column(Modifier.fillMaxSize()) {
            Header(
                onClear = { lines.clear() },
                onCopy = { clipboard.setText(AnnotatedString(ordered.joinToString("\n") { it.plain() })) },
                onClose = onClose,
            )
            Spacer(Modifier.height(14.dp))
            StateGrid(state)
            Spacer(Modifier.height(14.dp))
            LogList(ordered, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Header(onClear: () -> Unit, onCopy: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(Strings.DEBUG_TITLE, color = Theme.Fg1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(Strings.DEBUG_HINT, color = Theme.Fg3, fontSize = 11.5.sp)
        Spacer(Modifier.weight(1f))
        PillButton(Strings.DEBUG_COPY, onCopy)
        Spacer(Modifier.width(8.dp))
        PillButton(Strings.DEBUG_CLEAR, onClear)
        Spacer(Modifier.width(8.dp))
        PillButton("✕", onClose)
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Theme.RadiusBtn.dp))
            .background(Theme.Surface3)
            .border(1.dp, Theme.Line2, RoundedCornerShape(Theme.RadiusBtn.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = Theme.Fg2, fontSize = 12.sp)
    }
}

@Composable
private fun StateGrid(state: UiState) {
    val server = state.server
    val serverText = if (server?.process?.isAlive == true) {
        String.format(Strings.DEBUG_SERVER_RUNNING, server.process.pid())
    } else {
        Strings.DEBUG_SERVER_STOPPED
    }
    val javaText = when (val src = state.settings.javaSource) {
        is JavaSource.Custom -> String.format(Strings.DEBUG_JAVA_CUSTOM, src.path)
        JavaSource.Bundled -> state.installed.javaVersion
            ?.let { String.format(Strings.DEBUG_JAVA_BUNDLED, it) }
            ?: Strings.DEBUG_JAVA_BUNDLED_MISSING
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.RadiusCard.dp))
            .background(Theme.Surface1)
            .border(1.dp, Theme.Line1, RoundedCornerShape(Theme.RadiusCard.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatRow(Strings.DEBUG_STAT_STATUS, state.status.name)
        StatRow(Strings.DEBUG_STAT_XMAGE, state.installed.xmageVersion ?: Strings.DEBUG_VALUE_NONE)
        StatRow(Strings.DEBUG_STAT_JAVA, javaText)
        StatRow(Strings.DEBUG_STAT_SERVER, serverText)
        StatRow(Strings.DEBUG_STAT_CLIENTS, state.clients.size.toString())
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Theme.Fg3, fontSize = 12.sp, modifier = Modifier.width(96.dp))
        Text(
            value,
            color = Theme.Fg1,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun LogList(lines: List<DebugLine>, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.RadiusCard.dp))
            .background(Color(0xFF08090C))
            .border(1.dp, Theme.Line1, RoundedCornerShape(Theme.RadiusCard.dp)),
    ) {
        if (lines.isEmpty()) {
            Text(
                Strings.DEBUG_EMPTY,
                color = Theme.Fg3,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            return@Box
        }
        val listState = rememberLazyListState()
        LaunchedEffect(lines.size) {
            if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(10.dp)) {
            items(lines) { line -> LogRow(line) }
        }
    }
}

@Composable
private fun LogRow(line: DebugLine) {
    Row {
        Text(
            line.time,
            color = Theme.Fg4,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "[${line.tag}]",
            color = tagColor(line.tag),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            line.text,
            color = levelColor(line.level),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

private fun DebugLine.plain(): String = "$time [$tag] ${level?.let { "${it.name} " } ?: ""}$text"

private fun LogLine.toDebugLine(tag: String) = DebugLine(
    timestampMs = timestampMs,
    time = AppLog.timeOf(this),
    tag = tag,
    level = level,
    text = text,
)

private fun MutableList<DebugLine>.addTrimmed(line: DebugLine) {
    add(line)
    if (size > MAX_LINES) repeat(size - MAX_LINES) { removeAt(0) }
}

private fun levelColor(level: LogLevel?): Color = when (level) {
    LogLevel.ERROR -> Theme.StatusErrText
    LogLevel.WARN -> Theme.StatusWarnText
    LogLevel.INFO -> Theme.Fg2
    LogLevel.DEBUG -> Theme.Fg3
    null -> Theme.Fg2 // raw process output
}

private fun tagColor(tag: String): Color = when {
    tag == "app" -> Theme.StatusReady
    tag == "server" -> Theme.StatusOk
    else -> Theme.StatusWarn // client N
}
