package ie.stu.invoker.process

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Launcher-level diagnostic log — distinct from [ProcessLog], which carries the raw stdout/stderr
 * of a running XMage client or server. AppLog records what the *launcher itself* is doing: update
 * checks, Java resolution, process spawns and, crucially, why they fail. Two sinks:
 *
 *  - an in-memory replay [SharedFlow] the F3 debug overlay subscribes to, and
 *  - best-effort append to `xmage_launcher.log` on disk (wired up in [ie.stu.invoker.AppEnvironment]).
 *
 * A singleton so it can be reached from anywhere in the launch path without threading it through
 * every constructor; there is only ever one launcher process.
 */
object AppLog {

    private val _lines = MutableSharedFlow<LogLine>(replay = 500, extraBufferCapacity = 1000)
    val lines: SharedFlow<LogLine> = _lines.asSharedFlow()

    @Volatile private var file: Path? = null
    private val wallClock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    private val stampFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    /**
     * Point the on-disk sink at [path]. Safe to call once at startup; anything logged before this
     * still reaches the in-memory flow, it just isn't persisted. A leading session banner makes
     * separate runs easy to tell apart in the file.
     */
    fun setLogFile(path: Path) {
        runCatching { Files.createDirectories(path.parent) }
        file = path
        i("──── Invoker session started ────")
    }

    fun d(msg: String) = log(LogLevel.DEBUG, msg)
    fun i(msg: String) = log(LogLevel.INFO, msg)
    fun w(msg: String) = log(LogLevel.WARN, msg)

    fun e(msg: String, t: Throwable? = null) = log(
        LogLevel.ERROR,
        if (t != null) "$msg — ${t.javaClass.simpleName}: ${t.message}" else msg,
    )

    fun log(level: LogLevel, msg: String) {
        val line = LogLine(text = msg, level = level)
        _lines.tryEmit(line)
        appendToFile(line)
    }

    @Synchronized
    private fun appendToFile(line: LogLine) {
        val target = file ?: return
        val stamp = stampFmt.format(Instant.ofEpochMilli(line.timestampMs))
        val row = "$stamp [${line.level?.name ?: "PROC"}] ${line.text}\n"
        runCatching {
            Files.write(
                target,
                row.toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    /** Wall-clock time of a line, for the overlay gutter. */
    fun timeOf(line: LogLine): String = wallClock.format(Instant.ofEpochMilli(line.timestampMs))
}
