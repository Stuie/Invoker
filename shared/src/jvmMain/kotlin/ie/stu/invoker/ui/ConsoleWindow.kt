package ie.stu.invoker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import ie.stu.invoker.process.LogLine
import ie.stu.invoker.process.XMageProcess
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun ConsoleWindow(title: String, process: XMageProcess) {
    val buffer = remember { mutableStateOf<List<LogLine>>(emptyList()) }
    LaunchedEffect(process) {
        process.log.lines.collect { line ->
            buffer.value = (buffer.value + line).takeLast(2000)
        }
    }
    var visible by remember { mutableStateOf(true) }
    if (!visible) return
    Window(
        onCloseRequest = { visible = false },
        title = title,
        state = rememberWindowState(width = 900.dp, height = 480.dp),
    ) {
        val listState = rememberLazyListState()
        LaunchedEffect(buffer.value.size) {
            if (buffer.value.isNotEmpty()) listState.scrollToItem(buffer.value.lastIndex)
        }
        Box(Modifier.fillMaxSize().background(Color(0xFF101012))) {
            LazyColumn(state = listState, contentPadding = PaddingValues(8.dp)) {
                items(buffer.value) { line ->
                    Text(
                        line.text,
                        color = Color(0xFFD0D0D0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
