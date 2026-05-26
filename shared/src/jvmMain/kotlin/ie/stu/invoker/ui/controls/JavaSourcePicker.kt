package ie.stu.invoker.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.stu.invoker.platform.DetectedJava
import ie.stu.invoker.platform.DetectionSource
import ie.stu.invoker.platform.JavaValidationError
import ie.stu.invoker.settings.JavaSource
import ie.stu.invoker.ui.AppButton
import ie.stu.invoker.ui.ButtonSize
import ie.stu.invoker.ui.ButtonVariant
import ie.stu.invoker.ui.ChipTone
import ie.stu.invoker.ui.Strings
import ie.stu.invoker.ui.clickableNoRipple
import ie.stu.invoker.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Frame
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager

// ── Display formatting (UI's view of a DetectedJava) ────────────────────────

/**
 * Formats a `DetectedJava` for display. Lives in the UI layer so platform code stays free of
 * locale-bound copy.
 */
fun DetectedJava.displayName(): String =
    if (!vendor.isNullOrBlank()) String.format(Strings.JAVA_DISPLAY_NAME_WITH_VENDOR, rawVersion, vendor)
    else String.format(Strings.JAVA_DISPLAY_NAME, rawVersion)

private fun JavaValidationError.localisedMessage(): String = when (this) {
    is JavaValidationError.NotAValidJava -> String.format(Strings.JAVA_ERROR_NOT_VALID, expectedExeName)
}

// ── Trigger (sits in the right-hand `control` slot of a SettingRow) ──────────

/**
 * The summary chip + Change toggle. Compact; safe to drop into the right side of a SettingRow
 * without squeezing the row's label/desc column.
 */
@Composable
fun JavaSourceTrigger(
    current: JavaSource,
    detected: List<DetectedJava>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SummaryChip(current, detected)
        Spacer(Modifier.width(10.dp))
        AppButton(
            label = if (expanded) Strings.JAVA_BTN_DONE else Strings.JAVA_BTN_CHANGE,
            onClick = onToggle,
            variant = ButtonVariant.Outlined,
            size = ButtonSize.Md,
            // Anchor to the longer string so the button doesn't reshape while we swap labels.
            widthAnchor = Strings.JAVA_BTN_CHANGE,
        )
    }
}

// ── Panel (rendered full-width in the SettingRow's `detail` slot when open) ──

@Composable
fun JavaSourcePanel(
    current: JavaSource,
    detected: List<DetectedJava>,
    detecting: Boolean,
    onRefreshDetection: () -> Unit,
    onCommit: (JavaSource) -> Unit,
    onClose: () -> Unit,
    validate: suspend (Path) -> Result<DetectedJava>,
) {
    var pickerError by remember { mutableStateOf<String?>(null) }
    var overridePath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun clearFeedback() {
        pickerError = null
        overridePath = null
    }

    LaunchedEffect(Unit) { if (detected.isEmpty() && !detecting) onRefreshDetection() }

    val compatible = detected.filter { it.majorVersion == 8 }
    val orphanCustom = (current as? JavaSource.Custom)
        ?.takeIf { c -> compatible.none { it.home.toString() == c.path } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.RadiusCard.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Theme.Line2, RoundedCornerShape(Theme.RadiusCard.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Option(
            title = Strings.JAVA_OPTION_BUNDLED_TITLE,
            detail = Strings.JAVA_OPTION_BUNDLED_DETAIL,
            selected = current is JavaSource.Bundled,
            tone = ChipTone.Ready,
            onClick = {
                onCommit(JavaSource.Bundled)
                clearFeedback()
                onClose()
            },
        )

        if (orphanCustom != null) {
            Option(
                title = Strings.JAVA_OPTION_CUSTOM_TITLE,
                detail = orphanCustom.path,
                selected = true,
                tone = ChipTone.Ok,
                onClick = { onClose() },
            )
        }

        compatible.forEach { det ->
            Option(
                title = det.displayName(),
                detail = det.home.toString() + if (det.source == DetectionSource.JavaHomeEnv) Strings.JAVA_OPTION_HOME_ENV_SUFFIX else "",
                selected = current is JavaSource.Custom && current.path == det.home.toString(),
                tone = ChipTone.Ok,
                onClick = {
                    onCommit(JavaSource.Custom(det.home.toString()))
                    clearFeedback()
                    onClose()
                },
            )
        }

        Option(
            title = Strings.JAVA_OPTION_CHOOSE_FOLDER_TITLE,
            detail = Strings.JAVA_OPTION_CHOOSE_FOLDER_DETAIL,
            selected = false,
            tone = ChipTone.Neutral,
            onClick = {
                scope.launch {
                    val picked = pickFolder() ?: return@launch
                    val result = validate(picked)
                    result.onSuccess { det ->
                        if (det.majorVersion != 8) {
                            pickerError = String.format(Strings.JAVA_ERROR_WRONG_VERSION, det.displayName())
                            overridePath = det.home.toString()
                        } else {
                            onCommit(JavaSource.Custom(det.home.toString()))
                            clearFeedback()
                            onClose()
                        }
                    }.onFailure { e ->
                        pickerError = when (e) {
                            is JavaValidationError -> e.localisedMessage()
                            else -> e.message ?: Strings.JAVA_ERROR_VALIDATION_GENERIC
                        }
                        overridePath = picked.toAbsolutePath().toString()
                    }
                }
            },
        )

        DetectionStatus(compatible.size, detecting, onRefreshDetection)

        if (pickerError != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(pickerError!!, color = Theme.StatusErrText, fontSize = 12.sp)
                val path = overridePath
                if (path != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppButton(
                            label = Strings.JAVA_OVERRIDE_BUTTON,
                            onClick = {
                                onCommit(JavaSource.Custom(path))
                                clearFeedback()
                                onClose()
                            },
                            variant = ButtonVariant.Outlined,
                            size = ButtonSize.Md,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            Strings.JAVA_OVERRIDE_HINT,
                            color = Theme.Fg3,
                            fontSize = 11.5.sp,
                        )
                    }
                }
            }
        }
    }
}

// ── Internals ────────────────────────────────────────────────────────────────

@Composable
private fun DetectionStatus(compatibleCount: Int, detecting: Boolean, onRefresh: () -> Unit) {
    if (detecting) {
        Text(
            Strings.JAVA_DETECTION_SCANNING,
            color = Theme.Fg3,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
        return
    }
    val foundText = if (compatibleCount == 1) Strings.JAVA_DETECTION_FOUND_SINGULAR
    else String.format(Strings.JAVA_DETECTION_FOUND_PLURAL, compatibleCount)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(foundText, color = Theme.Fg3, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(
            Strings.JAVA_DETECTION_REFRESH,
            color = Theme.Fg2,
            fontSize = 12.sp,
            modifier = Modifier.clickableNoRipple(onRefresh).padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun SummaryChip(current: JavaSource, detected: List<DetectedJava>) {
    val (title, sub) = when (current) {
        JavaSource.Bundled -> Strings.JAVA_SUMMARY_BUNDLED_TITLE to Strings.JAVA_SUMMARY_BUNDLED_SUB
        is JavaSource.Custom -> {
            val match = detected.firstOrNull { it.home.toString() == current.path }
            (match?.displayName() ?: Strings.JAVA_SUMMARY_CUSTOM_TITLE) to current.path
        }
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(title, color = Theme.Fg1, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(
            sub,
            color = Theme.Fg3,
            fontSize = 11.5.sp,
            fontFamily = if (current is JavaSource.Custom) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun Option(
    title: String,
    detail: String,
    selected: Boolean,
    tone: ChipTone,
    onClick: () -> Unit,
) {
    val dotColor = when (tone) {
        ChipTone.Ok -> Theme.StatusOk
        ChipTone.Ready -> Theme.StatusReady
        ChipTone.Warn -> Theme.StatusWarn
        ChipTone.Err -> Theme.StatusErr
        else -> Theme.Fg3
    }
    val bg = if (selected) Color.White.copy(alpha = 0.06f) else Color.Transparent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Theme.Fg1, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                detail,
                color = Theme.Fg3,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Text(Strings.JAVA_OPTION_SELECTED, color = Theme.Fg2, fontSize = 11.5.sp)
        }
    }
}

/** Open the platform's native directory picker. Returns null if the user cancels. */
private suspend fun pickFolder(): Path? = withContext(Dispatchers.IO) {
    var result: Path? = null
    SwingUtilities.invokeAndWait {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = Strings.JAVA_DIALOG_TITLE
        }
        val rc = chooser.showOpenDialog(null as Frame?)
        if (rc == JFileChooser.APPROVE_OPTION) {
            result = chooser.selectedFile.toPath()
        }
    }
    result
}
