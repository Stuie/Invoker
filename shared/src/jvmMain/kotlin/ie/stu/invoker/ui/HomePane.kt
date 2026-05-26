package ie.stu.invoker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Download
import com.composables.icons.materialsymbols.outlined.Play_arrow
import com.composables.icons.materialsymbols.outlined.Refresh
import com.composables.icons.materialsymbols.outlined.Stop
import com.composables.icons.materialsymbols.outlined.Warning
import com.composables.icons.materialsymbols.outlined.Wifi_off
import com.composables.icons.materialsymbols.outlined.Dns
import ie.stu.invoker.ui.theme.Theme

@Composable
fun HomePane(viewModel: MainViewModel, state: UiState) {
    val homeState = state.toHomeState()
    Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 48.dp)) {
        HeroBlock(homeState, state, Modifier.weight(1f))
        ActionBar(homeState, state, viewModel)
    }
}

// ── Hero block ───────────────────────────────────────────────────────────────

@Composable
private fun HeroBlock(homeState: HomeState, state: UiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusChip(homeState.chipTone, homeState.chipLabel)
        Spacer(Modifier.height(18.dp))
        Image(
            painter = xmageLabelPainter(),
            contentDescription = Strings.CONTENT_DESC_WORDMARK,
            modifier = Modifier.width(320.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            Strings.HOME_TAGLINE,
            color = Theme.Fg3,
            fontSize = 13.sp,
            letterSpacing = 0.10.em,
        )
        Spacer(Modifier.height(12.dp))
        VersionLine(homeState, state)
    }
}

@Composable
private fun VersionLine(homeState: HomeState, state: UiState) {
    val parts = homeState.versionParts(state)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        parts.forEachIndexed { idx, p ->
            if (idx > 0) Box(Modifier.size(4.dp).clip(CircleShape).background(Theme.Fg4))
            Text(
                p.text,
                color = Theme.Fg2,
                fontSize = 13.sp,
                fontFamily = if (p.mono) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

// ── Action bar ───────────────────────────────────────────────────────────────

@Composable
private fun ActionBar(homeState: HomeState, state: UiState, vm: MainViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.Line1))
        Spacer(Modifier.height(4.dp))
        when (homeState) {
            HomeState.Fresh -> FreshActions(state, vm)
            HomeState.UpToDate -> ReadyActions(state, vm)
            HomeState.UpdateAvailable -> UpdateAvailableActions(state, vm)
            HomeState.Downloading -> DownloadingActions(state)
            HomeState.Offline -> OfflineActions(vm)
        }
        ActionMeta(homeState, state, vm)
    }
}

@Composable
private fun FreshActions(state: UiState, vm: MainViewModel) {
    val ver = state.remote?.xmage?.version ?: Strings.PLACEHOLDER_VERSION
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        AppButton(
            label = String.format(Strings.HOME_BTN_INSTALL_XMAGE, ver),
            onClick = { vm.runUpdates() },
            variant = ButtonVariant.Filled,
            size = ButtonSize.Xl,
            icon = MaterialSymbols.Outlined.Download,
            enabled = !state.busy && state.remote != null,
        )
    }
    LaunchButtonRow(state, vm, dimmed = true, enabled = false)
}

@Composable
private fun ReadyActions(state: UiState, vm: MainViewModel) {
    LaunchButtonRow(state, vm, dimmed = false, enabled = state.canLaunch && !state.busy)
}

@Composable
private fun UpdateAvailableActions(state: UiState, vm: MainViewModel) {
    val installed = state.installed.xmageVersion ?: Strings.PLACEHOLDER_VERSION
    val latest = state.remote?.xmage?.version ?: Strings.PLACEHOLDER_VERSION
    UpdateBanner(
        heading = String.format(Strings.HOME_BANNER_UPDATE_HEADING, latest),
        subheading = String.format(Strings.HOME_BANNER_UPDATE_SUB, installed),
        icon = MaterialSymbols.Outlined.Warning,
        tone = ChipTone.Warn,
        actionLabel = Strings.HOME_BTN_INSTALL_UPDATE,
        onAction = { vm.runUpdates() },
        actionVariant = ButtonVariant.Filled,
    )
    LaunchButtonRow(state, vm, dimmed = false, enabled = state.canLaunch && !state.busy)
}

@Composable
private fun DownloadingActions(state: UiState) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                state.progressLabel ?: Strings.HOME_PROGRESS_DEFAULT_LABEL,
                color = Theme.Fg2,
                fontSize = 13.sp,
            )
            val pct = state.progress.fraction?.let { String.format(Strings.HOME_PROGRESS_PERCENT, (it * 100).toInt()) } ?: ""
            Text(pct, color = Theme.Fg2, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(state.progress.fraction)
    }
    DisabledLaunchRow()
}

@Composable
private fun OfflineActions(vm: MainViewModel) {
    UpdateBanner(
        heading = Strings.HOME_BANNER_OFFLINE_HEADING,
        subheading = Strings.HOME_BANNER_OFFLINE_SUB,
        icon = MaterialSymbols.Outlined.Wifi_off,
        tone = ChipTone.Err,
        actionLabel = Strings.HOME_BTN_RETRY,
        onAction = { vm.checkUpdates() },
        actionVariant = ButtonVariant.Outlined,
    )
    DisabledLaunchRow()
}

@Composable
private fun LaunchButtonRow(state: UiState, vm: MainViewModel, dimmed: Boolean, enabled: Boolean) {
    Row(
        Modifier.fillMaxWidth().alpha(if (dimmed) 0.45f else 1f),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        AppButton(
            label = Strings.HOME_BTN_RUN_CLIENT,
            onClick = { vm.launchClient() },
            variant = ButtonVariant.Filled,
            size = ButtonSize.Lg,
            icon = MaterialSymbols.Outlined.Play_arrow,
            enabled = enabled,
        )
        AppButton(
            label = Strings.HOME_BTN_RUN_CLIENT_SERVER,
            onClick = { vm.launchClientAndServer() },
            variant = ButtonVariant.Tonal,
            size = ButtonSize.Lg,
            icon = MaterialSymbols.Outlined.Play_arrow,
            enabled = enabled && !state.serverRunning,
        )
        if (state.serverRunning) {
            AppButton(
                label = Strings.HOME_BTN_STOP_SERVER,
                onClick = { vm.stopServer() },
                variant = ButtonVariant.Tonal,
                size = ButtonSize.Lg,
                icon = MaterialSymbols.Outlined.Stop,
                tone = ChipTone.Err,
                enabled = enabled,
            )
        } else {
            AppButton(
                label = Strings.HOME_BTN_RUN_SERVER,
                onClick = { vm.launchServer() },
                variant = ButtonVariant.Tonal,
                size = ButtonSize.Lg,
                icon = MaterialSymbols.Outlined.Dns,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun DisabledLaunchRow() {
    Row(
        Modifier.fillMaxWidth().alpha(0.45f).padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        AppButton(label = Strings.HOME_BTN_RUN_CLIENT, onClick = {}, variant = ButtonVariant.Tonal, icon = MaterialSymbols.Outlined.Play_arrow, enabled = false)
        AppButton(label = Strings.HOME_BTN_RUN_CLIENT_SERVER, onClick = {}, variant = ButtonVariant.Tonal, icon = MaterialSymbols.Outlined.Play_arrow, enabled = false)
        AppButton(label = Strings.HOME_BTN_RUN_SERVER, onClick = {}, variant = ButtonVariant.Tonal, icon = MaterialSymbols.Outlined.Dns, enabled = false)
    }
}

@Composable
private fun ProgressBar(fraction: Float?) {
    val pct = (fraction ?: 0f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(pct)
                .height(4.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(listOf(Theme.StatusReady, Theme.StatusReady.copy(alpha = 0.6f)))
                )
        )
    }
}

@Composable
private fun ActionMeta(homeState: HomeState, state: UiState, vm: MainViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(homeState.metaText(state), color = Theme.Fg3, fontSize = 12.5.sp)
        when (homeState) {
            HomeState.UpToDate, HomeState.UpdateAvailable -> {
                AppButton(
                    label = Strings.HOME_BTN_CHECK_UPDATES,
                    onClick = { vm.checkUpdates() },
                    variant = ButtonVariant.Text,
                    icon = MaterialSymbols.Outlined.Refresh,
                )
            }
            HomeState.Offline -> {
                AppButton(
                    label = Strings.HOME_BTN_OPEN_SETTINGS,
                    onClick = { vm.navigateTo(Destination.Settings) },
                    variant = ButtonVariant.Text,
                )
            }
            else -> Spacer(Modifier.width(1.dp))
        }
    }
}

// ── State mapping ────────────────────────────────────────────────────────────

private enum class HomeState {
    Fresh, UpToDate, UpdateAvailable, Downloading, Offline;

    val chipTone: ChipTone get() = when (this) {
        Fresh -> ChipTone.Ready
        UpToDate -> ChipTone.Ok
        UpdateAvailable -> ChipTone.Warn
        Downloading -> ChipTone.Busy
        Offline -> ChipTone.Err
    }

    val chipLabel: String get() = when (this) {
        Fresh -> Strings.HOME_CHIP_NOT_INSTALLED
        UpToDate -> Strings.HOME_CHIP_UP_TO_DATE
        UpdateAvailable -> Strings.HOME_CHIP_UPDATE_AVAILABLE
        Downloading -> Strings.HOME_CHIP_DOWNLOADING
        Offline -> Strings.HOME_CHIP_OFFLINE
    }

    fun versionParts(state: UiState): List<VersionPart> {
        val installed = state.installed.xmageVersion
        val latest = state.remote?.xmage?.version
        return when (this) {
            Fresh -> listOf(
                VersionPart(String.format(Strings.HOME_VL_VERSION_PREFIX, latest ?: Strings.PLACEHOLDER_VERSION), mono = true),
                VersionPart(Strings.HOME_VL_READY_TO_INSTALL),
            )
            UpToDate -> listOf(
                VersionPart(installed ?: Strings.PLACEHOLDER_VERSION, mono = true),
                VersionPart(Strings.HOME_VL_INSTALLED),
            )
            UpdateAvailable -> listOf(
                VersionPart(installed ?: Strings.PLACEHOLDER_VERSION, mono = true),
                VersionPart(String.format(Strings.HOME_VL_UPDATE_TO_AVAILABLE, latest ?: Strings.PLACEHOLDER_VERSION)),
            )
            Downloading -> listOf(
                VersionPart(Strings.HOME_VL_WORKING_ON_IT),
                VersionPart(latest ?: installed ?: Strings.PLACEHOLDER_VERSION, mono = true),
            )
            Offline -> {
                val list = mutableListOf(VersionPart(Strings.HOME_VL_OFFLINE))
                if (installed != null) list += VersionPart(installed, mono = true)
                list
            }
        }
    }

    fun metaText(state: UiState): String = when (this) {
        Fresh -> Strings.HOME_META_FRESH
        UpToDate, UpdateAvailable ->
            if (state.serverRunning) Strings.HOME_META_SERVER_RUNNING else Strings.HOME_META_LAST_CHECKED
        Downloading -> Strings.HOME_META_DOWNLOADING
        Offline -> Strings.HOME_META_OFFLINE
    }
}

private data class VersionPart(val text: String, val mono: Boolean = false)

private fun UiState.toHomeState(): HomeState = when {
    busy -> HomeState.Downloading
    status == InstallStatus.Offline -> HomeState.Offline
    status == InstallStatus.NotInstalled -> HomeState.Fresh
    status == InstallStatus.UpdateAvailable -> HomeState.UpdateAvailable
    status == InstallStatus.UpToDate -> HomeState.UpToDate
    else -> HomeState.UpToDate
}
