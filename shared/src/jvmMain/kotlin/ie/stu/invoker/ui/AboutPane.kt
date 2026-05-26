package ie.stu.invoker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Download
import ie.stu.invoker.BuildInfo
import ie.stu.invoker.ui.theme.Theme

private enum class AboutState { NotInstalled, UpdateAvailable, UpToDate, Offline }

private data class AboutAction(val label: String, val tone: ChipTone)

@Composable
fun AboutPane(state: UiState, viewModel: MainViewModel) {
    val aboutState = state.toAboutState()
    val latest = state.remote?.xmage?.version

    Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 44.dp, bottom = 28.dp)) {
        PaneHeader(
            title = Strings.ABOUT,
            trailing = { HeaderChip(aboutState, latest) },
        )

        AboutBlock(aboutState, viewModel)
        Spacer(Modifier.height(14.dp))
        AttributesList(state)
    }
}

@Composable
private fun HeaderChip(aboutState: AboutState, latest: String?) = when (aboutState) {
    AboutState.NotInstalled ->
        StatusChip(
            ChipTone.Ready,
            if (latest != null) String.format(Strings.ABOUT_CHIP_READY_TO_INSTALL, latest)
            else Strings.ABOUT_CHIP_READY_TO_INSTALL_NO_VERSION,
        )
    AboutState.UpdateAvailable ->
        StatusChip(
            ChipTone.Warn,
            if (latest != null) String.format(Strings.ABOUT_CHIP_UPDATE_AVAILABLE, latest)
            else Strings.ABOUT_CHIP_UPDATE_AVAILABLE_NO_VERSION,
        )
    AboutState.UpToDate -> StatusChip(ChipTone.Ok, Strings.ABOUT_CHIP_UP_TO_DATE)
    AboutState.Offline -> StatusChip(ChipTone.Err, Strings.ABOUT_CHIP_OFFLINE)
}

@Composable
private fun AboutBlock(aboutState: AboutState, vm: MainViewModel) {
    val action = aboutState.action

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.RadiusCard.dp))
            .background(Theme.Surface1)
            .border(1.dp, Theme.Line1, RoundedCornerShape(Theme.RadiusCard.dp))
            .padding(22.dp),
    ) {
        BrandTile(size = 60)
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Strings.ABOUT_APP_NAME,
                color = Theme.Fg1,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.01).em,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text(Strings.ABOUT_APP_DESCRIPTION_PREFIX, color = Theme.Fg3, fontSize = 13.sp)
                Text(
                    String.format(Strings.HOME_VL_VERSION_PREFIX, BuildInfo.LAUNCHER_VERSION),
                    color = Theme.Fg3,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (action != null) {
            AppButton(
                label = action.label,
                onClick = { vm.runUpdates() },
                variant = ButtonVariant.Filled,
                tone = action.tone.takeIf { it != ChipTone.Ready }, // Ready → default primary fill
                icon = MaterialSymbols.Outlined.Download,
            )
        }
    }
}

@Composable
private fun AttributesList(state: UiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.RadiusCard.dp))
            .background(Theme.Surface1)
            .border(1.dp, Theme.Line1, RoundedCornerShape(Theme.RadiusCard.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        val rows = buildList {
            add(Strings.ABOUT_ATTR_XMAGE_CLIENT to (state.installed.xmageVersion ?: Strings.ABOUT_VALUE_NOT_INSTALLED))
            state.remote?.xmage?.version?.let { add(Strings.ABOUT_ATTR_LATEST to it) }
            add(Strings.ABOUT_ATTR_CHANNEL to state.settings.xmageBranch.name)
            add(Strings.ABOUT_ATTR_LICENSE to Strings.ABOUT_VALUE_LICENSE)
        }
        rows.forEachIndexed { idx, (k, v) ->
            if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.Line1))
            AttribRow(k, v)
        }
    }
}

@Composable
private fun AttribRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = Theme.Fg3, fontSize = 13.sp)
        Text(value, color = Theme.Fg1, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun UiState.toAboutState(): AboutState = when {
    status == InstallStatus.Offline && installed.xmageVersion == null -> AboutState.Offline
    installed.xmageVersion == null -> AboutState.NotInstalled
    plan?.needsXMage == true -> AboutState.UpdateAvailable
    else -> AboutState.UpToDate
}

private val AboutState.action: AboutAction?
    get() = when (this) {
        AboutState.NotInstalled -> AboutAction(Strings.ABOUT_BTN_INSTALL_XMAGE, ChipTone.Ready)
        AboutState.UpdateAvailable -> AboutAction(Strings.ABOUT_BTN_INSTALL_UPDATE, ChipTone.Warn)
        AboutState.UpToDate, AboutState.Offline -> null
    }
