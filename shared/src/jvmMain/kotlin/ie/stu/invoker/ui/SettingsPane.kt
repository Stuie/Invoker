package ie.stu.invoker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ie.stu.invoker.settings.UserSettings
import ie.stu.invoker.ui.controls.AppSwitch
import ie.stu.invoker.ui.controls.AppTextField
import ie.stu.invoker.ui.controls.JavaSourcePanel
import ie.stu.invoker.ui.controls.JavaSourceTrigger
import ie.stu.invoker.ui.controls.Segment
import ie.stu.invoker.ui.controls.SegmentOption
import ie.stu.invoker.ui.theme.Theme

@Composable
fun SettingsPane(state: UiState, viewModel: MainViewModel) {
    val current = state.settings
    val onChange: (UserSettings) -> Unit = viewModel::applySettings
    var javaExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 44.dp, bottom = 28.dp)) {
        PaneHeader(Strings.SETTINGS, subtitle = Strings.SETTINGS_SUBTITLE)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SettingsGroup(Strings.SETTINGS_GROUP_BRANCH_RUNTIME) {
                SettingRow(
                    label = Strings.SETTINGS_CHANNEL_LABEL,
                    desc = Strings.SETTINGS_CHANNEL_DESC,
                ) {
                    Segment(
                        options = listOf(
                            SegmentOption(UserSettings.Branch.Main, Strings.SETTINGS_CHANNEL_MAIN),
                            SegmentOption(UserSettings.Branch.Custom, Strings.SETTINGS_CHANNEL_CUSTOM),
                        ),
                        value = current.xmageBranch,
                        onChange = { branch ->
                            val homeUrl = if (branch == UserSettings.Branch.Main) UserSettings.MAIN_BRANCH_URL else current.xmageHomeUrl
                            onChange(current.copy(xmageBranch = branch, xmageHomeUrl = homeUrl))
                        },
                    )
                }
                SettingsDivider()
                SettingRow(
                    label = Strings.SETTINGS_JAVA_LABEL,
                    desc = Strings.SETTINGS_JAVA_DESC,
                    control = {
                        JavaSourceTrigger(
                            current = current.javaSource,
                            detected = state.detectedJavas,
                            expanded = javaExpanded,
                            onToggle = { javaExpanded = !javaExpanded },
                        )
                    },
                    detail = if (javaExpanded) {
                        {
                            JavaSourcePanel(
                                current = current.javaSource,
                                detected = state.detectedJavas,
                                detecting = state.javaDetectionRunning,
                                onRefreshDetection = viewModel::refreshJavaDetection,
                                onCommit = { src -> onChange(current.copy(javaSource = src)) },
                                onClose = { javaExpanded = false },
                                validate = viewModel::validateJavaPath,
                            )
                        }
                    } else null,
                )
                SettingsDivider()
                SettingRow(
                    label = Strings.SETTINGS_HOME_URL_LABEL,
                    desc = Strings.SETTINGS_HOME_URL_DESC,
                ) {
                    AppTextField(
                        value = current.xmageHomeUrl,
                        onChange = { onChange(current.copy(xmageHomeUrl = it, xmageBranch = UserSettings.Branch.Custom)) },
                        width = 280.dp,
                    )
                }
            }

            SettingsGroup(Strings.SETTINGS_GROUP_CONSOLE) {
                SettingRow(
                    label = Strings.SETTINGS_SHOW_CLIENT_CONSOLE_LABEL,
                    desc = Strings.SETTINGS_SHOW_CLIENT_CONSOLE_DESC,
                ) {
                    AppSwitch(on = current.showClientConsole, onChange = { onChange(current.copy(showClientConsole = it)) })
                }
                SettingsDivider()
                SettingRow(
                    label = Strings.SETTINGS_SHOW_SERVER_CONSOLE_LABEL,
                    desc = Strings.SETTINGS_SHOW_SERVER_CONSOLE_DESC,
                ) {
                    AppSwitch(on = current.showServerConsole, onChange = { onChange(current.copy(showServerConsole = it)) })
                }
                SettingsDivider()
                SettingRow(
                    label = Strings.SETTINGS_TEST_MODE_LABEL,
                    desc = Strings.SETTINGS_TEST_MODE_DESC,
                ) {
                    AppSwitch(on = current.serverTestMode, onChange = { onChange(current.copy(serverTestMode = it)) })
                }
            }

            SettingsGroup(Strings.SETTINGS_GROUP_ADVANCED_JAVA) {
                SettingRow(
                    label = Strings.SETTINGS_CLIENT_OPTS_LABEL,
                    desc = Strings.SETTINGS_CLIENT_OPTS_DESC,
                ) {
                    AppTextField(
                        value = current.clientJvmOpts,
                        onChange = { onChange(current.copy(clientJvmOpts = it)) },
                        width = 320.dp,
                    )
                }
                SettingsDivider()
                SettingRow(
                    label = Strings.SETTINGS_SERVER_OPTS_LABEL,
                    desc = Strings.SETTINGS_SERVER_OPTS_DESC,
                ) {
                    AppTextField(
                        value = current.serverJvmOpts,
                        onChange = { onChange(current.copy(serverJvmOpts = it)) },
                        width = 320.dp,
                    )
                }
                SettingsDivider()
                SettingRow(
                    label = Strings.SETTINGS_STARTUP_DELAY_LABEL,
                    desc = Strings.SETTINGS_STARTUP_DELAY_DESC,
                ) {
                    AppTextField(
                        value = current.clientStartupDelaySeconds.toString(),
                        onChange = { v ->
                            v.toIntOrNull()?.let { onChange(current.copy(clientStartupDelaySeconds = it)) }
                        },
                        width = 100.dp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(Theme.RadiusCard.dp))
            .background(Theme.Surface1)
            .border(1.dp, Theme.Line1, RoundedCornerShape(Theme.RadiusCard.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Text(
            title.uppercase(),
            color = Theme.Fg3,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.10.em,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
        )
        Column(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
            content()
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Canonical row layout: title+description on the left (weight 1), control on the right,
 * vertically centered against the taller of the two. Optional [detail] slot renders full-width
 * below the row — used by the Java picker so its expanded panel doesn't squeeze the desc into
 * a one-character-per-line column.
 */
@Composable
private fun SettingRow(
    label: String,
    desc: String,
    control: @Composable () -> Unit,
    detail: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsLabels(Modifier.weight(1f), label = label, desc = desc)
            control()
        }
        if (detail != null) {
            Spacer(Modifier.height(12.dp))
            detail()
        }
    }
}

@Composable
private fun SettingRow(label: String, desc: String, control: @Composable () -> Unit) {
    SettingRow(label = label, desc = desc, control = control, detail = null)
}

@Composable
private fun SettingsLabels(modifier: Modifier, label: String, desc: String) {
    Column(modifier.padding(end = 18.dp)) {
        Text(label, color = Theme.Fg1, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(desc, color = Theme.Fg3, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.Line1))
}
