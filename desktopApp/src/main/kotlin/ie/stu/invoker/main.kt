package ie.stu.invoker

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ie.stu.invoker.ui.Strings
import java.awt.Dimension

private const val MIN_WIDTH = 960
private const val MIN_HEIGHT = 700
private const val DEFAULT_WIDTH = 1040
private const val DEFAULT_HEIGHT = 740

fun main() = application {
    // Loaded from the classpath (desktopApp/icons/icon.png is wired in via build.gradle.kts).
    // This sets the AWT/Skiko window icon for the *running* app — gradlew :run, hotRun, and
    // jpackage builds alike. The `nativeDistributions { iconFile = … }` settings only embed
    // icons in the OS-level installer wrapping (taskbar icon for an installed app); they
    // don't affect a JVM launched directly.
    val windowIcon = painterResource("icon.png")
    Window(
        onCloseRequest = ::exitApplication,
        title = Strings.FRAME_TITLE,
        icon = windowIcon,
        state = rememberWindowState(width = DEFAULT_WIDTH.dp, height = DEFAULT_HEIGHT.dp),
    ) {
        LaunchedEffect(window) {
            window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
        }
        App()
    }
}
