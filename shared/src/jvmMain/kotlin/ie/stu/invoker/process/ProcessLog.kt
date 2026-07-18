package ie.stu.invoker.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * One line of output. [level] is null for raw XMage process output (from [ProcessLog]) and
 * non-null for launcher-level diagnostics (from [AppLog]); the debug overlay colours by it.
 */
data class LogLine(
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val level: LogLevel? = null,
)

class ProcessLog private constructor() {

    private val _lines = MutableSharedFlow<LogLine>(replay = 500, extraBufferCapacity = 1000)
    val lines: SharedFlow<LogLine> = _lines.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun pump(stream: InputStream) {
        scope.launch {
            BufferedReader(InputStreamReader(stream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    _lines.emit(LogLine(line))
                }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    companion object {
        fun attach(process: Process): ProcessLog {
            val log = ProcessLog()
            // ProcessBuilder is configured with redirectErrorStream — both stdout and stderr arrive on inputStream.
            log.pump(process.inputStream)
            process.onExit().thenRun { log.close() }
            return log
        }
    }
}
