package ie.stu.invoker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Info
import ie.stu.invoker.decks.CardResult
import ie.stu.invoker.decks.CardStatus
import ie.stu.invoker.decks.ImageQuality
import ie.stu.invoker.ui.controls.Segment
import ie.stu.invoker.ui.controls.SegmentOption
import ie.stu.invoker.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Frame
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun DecksPane(state: UiState, viewModel: MainViewModel) {
    val ds = state.deckSync
    val scope = rememberCoroutineScope()
    var urlText by remember { mutableStateOf("") }
    // Image fetching writes into XMage's image cache and reads its card DB, so it needs XMage installed.
    val xmageInstalled = state.installed.xmageVersion != null

    Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 44.dp, bottom = 28.dp)) {
        PaneHeader(Strings.DECKS, subtitle = Strings.DECKS_SUBTITLE)

        if (!xmageInstalled) {
            RequiresXMageBanner(onGoHome = { viewModel.navigateTo(Destination.Home) })
            Spacer(Modifier.height(18.dp))
        }

        // Quality selector
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
            Text(Strings.DECKS_QUALITY_LABEL, color = Theme.Fg2, fontSize = 13.sp)
            Spacer(Modifier.width(12.dp))
            Segment(
                options = listOf(
                    SegmentOption(ImageQuality.Small, Strings.DECKS_QUALITY_SMALL),
                    SegmentOption(ImageQuality.Normal, Strings.DECKS_QUALITY_NORMAL),
                    SegmentOption(ImageQuality.Large, Strings.DECKS_QUALITY_LARGE),
                    SegmentOption(ImageQuality.Best, Strings.DECKS_QUALITY_BEST),
                ),
                value = state.settings.deckImageQuality,
                onChange = viewModel::setDeckImageQuality,
            )
        }

        // Paste area
        DeckTextArea(
            value = ds.rawInput,
            onChange = viewModel::onDeckInputChanged,
            enabled = !ds.running,
        )

        Spacer(Modifier.height(12.dp))

        // Import-from-URL row
        Row(verticalAlignment = Alignment.CenterVertically) {
            SingleLineField(
                value = urlText,
                onChange = { urlText = it },
                placeholder = Strings.DECKS_URL_PLACEHOLDER,
                modifier = Modifier.weight(1f),
                enabled = !ds.running && !ds.importing,
            )
            Spacer(Modifier.width(10.dp))
            AppButton(
                label = Strings.DECKS_BTN_IMPORT_URL,
                onClick = { viewModel.importFromUrl(urlText) },
                variant = ButtonVariant.Outlined,
                enabled = urlText.isNotBlank() && !ds.running && !ds.importing,
            )
        }

        Spacer(Modifier.height(14.dp))

        // Action row
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppButton(
                label = Strings.DECKS_BTN_OPEN_DCK,
                onClick = {
                    scope.launch {
                        val picked = pickDckFile() ?: return@launch
                        viewModel.loadDeckFile(picked)
                    }
                },
                variant = ButtonVariant.Outlined,
                enabled = !ds.running,
            )
            Spacer(Modifier.weight(1f))
            AppButton(
                label = if (ds.running) Strings.DECKS_BTN_SYNCING else Strings.DECKS_BTN_SYNC,
                onClick = viewModel::syncDeck,
                variant = ButtonVariant.Filled,
                size = ButtonSize.Lg,
                enabled = ds.rawInput.isNotBlank() && !ds.running && xmageInstalled,
                widthAnchor = Strings.DECKS_BTN_SYNCING,
            )
        }

        // Feedback: hint / error
        val message = ds.error ?: ds.hint
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                color = if (ds.error != null) Theme.StatusErrText else Theme.Fg3,
                fontSize = 12.5.sp,
            )
        }

        // Progress + results
        if (ds.running || ds.results.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ProgressAndResults(ds.completed, ds.total, ds.results, ds.running, ds.ignoredLines)
        }
    }
}

@Composable
private fun RequiresXMageBanner(onGoHome: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.RadiusCard.dp))
            .background(Theme.StatusWarnBg)
            .border(1.dp, Theme.StatusWarn.copy(alpha = 0.35f), RoundedCornerShape(Theme.RadiusCard.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            MaterialSymbols.Outlined.Info,
            contentDescription = null,
            tint = Theme.StatusWarn,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Strings.DECKS_REQUIRES_XMAGE_TITLE,
                color = Theme.StatusWarnText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                Strings.DECKS_REQUIRES_XMAGE_BODY,
                color = Theme.Fg2,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        AppButton(
            label = Strings.DECKS_REQUIRES_XMAGE_ACTION,
            onClick = onGoHome,
            variant = ButtonVariant.Outlined,
        )
    }
}

@Composable
private fun ProgressAndResults(
    completed: Int,
    total: Int,
    results: List<CardResult>,
    running: Boolean,
    ignoredLines: Int,
) {
    val fraction = if (total > 0) completed.toFloat() / total.toFloat() else 0f

    if (running || total > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text(Strings.DECKS_PROGRESS_LABEL, color = Theme.Fg2, fontSize = 12.5.sp)
            Spacer(Modifier.width(10.dp))
            Text(String.format(Strings.DECKS_PROGRESS_COUNT, completed, total), color = Theme.Fg3, fontSize = 12.5.sp)
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Theme.Line1),
        ) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Theme.Primary))
        }
    }

    if (!running && results.isNotEmpty()) {
        val downloaded = results.count { it.status == CardStatus.Downloaded }
        val skipped = results.count { it.status == CardStatus.SkippedExisting }
        val notFound = results.count { it.status == CardStatus.NotFound || it.status == CardStatus.Failed }
        Spacer(Modifier.height(10.dp))
        Text(
            String.format(Strings.DECKS_SUMMARY, downloaded, skipped, notFound),
            color = Theme.Fg2,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
        )
        if (ignoredLines > 0) {
            Text(
                String.format(Strings.DECKS_IGNORED_LINES, ignoredLines),
                color = Theme.Fg3,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        results.forEach { ResultRow(it) }
    }
}

@Composable
private fun ResultRow(result: CardResult) {
    val (dot, label) = when (result.status) {
        CardStatus.Downloaded -> Theme.StatusOk to Strings.DECKS_STATUS_DOWNLOADED
        CardStatus.SkippedExisting -> Theme.StatusReady to Strings.DECKS_STATUS_SKIPPED
        CardStatus.NotFound -> Theme.StatusWarn to Strings.DECKS_STATUS_NOT_FOUND
        CardStatus.Failed -> Theme.StatusErr to Strings.DECKS_STATUS_FAILED
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(result.display, color = Theme.Fg1, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (result.setCode != null) {
            Text(
                result.setCode.uppercase(),
                color = Theme.Fg3,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(result.detail ?: label, color = Theme.Fg3, fontSize = 11.5.sp)
    }
}

// ── Local styled inputs (paste area needs full width / multi-line, which AppTextField isn't) ──

@Composable
private fun DeckTextArea(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (focused) Theme.Primary else Theme.Line2, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                interactionSource = interaction,
                cursorBrush = SolidColor(Theme.Primary),
                textStyle = TextStyle(color = Theme.Fg1, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            Strings.DECKS_INPUT_PLACEHOLDER,
                            color = Theme.Fg4,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun SingleLineField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        interactionSource = interaction,
        cursorBrush = SolidColor(Theme.Primary),
        textStyle = TextStyle(color = Theme.Fg1, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (focused) Theme.Primary else Theme.Line2, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, color = Theme.Fg4, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            inner()
        },
    )
}

/** Native file picker filtered to `.dck`. Returns null if the user cancels. */
private suspend fun pickDckFile(): Path? = withContext(Dispatchers.IO) {
    var result: Path? = null
    SwingUtilities.invokeAndWait {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            dialogTitle = Strings.DECKS_DIALOG_TITLE
            fileFilter = FileNameExtensionFilter("XMage deck (*.dck)", "dck")
        }
        val rc = chooser.showOpenDialog(null as Frame?)
        if (rc == JFileChooser.APPROVE_OPTION) {
            result = chooser.selectedFile.toPath()
        }
    }
    result
}
