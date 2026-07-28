package ie.stu.invoker

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ie.stu.invoker.ui.MainScreen
import ie.stu.invoker.ui.MainViewModel

@Composable
fun App(
    env: AppEnvironment = remember { AppEnvironment() },
    debugVisible: Boolean = false,
    onToggleDebug: () -> Unit = {},
    onCloseDebug: () -> Unit = {},
) {
    val viewModel = remember(env) { MainViewModel(env) }
    MaterialTheme(colorScheme = darkColorScheme()) {
        MainScreen(
            viewModel,
            debugVisible = debugVisible,
            onToggleDebug = onToggleDebug,
            onCloseDebug = onCloseDebug,
        )
    }
}
