package ie.stu.invoker.process

import ie.stu.invoker.install.Paths
import ie.stu.invoker.settings.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Files
import java.nio.file.Path

enum class XMageRole(val subdir: String, val mainClass: String) {
    Client("mage-client", "mage.client.MageFrame"),
    Server("mage-server", "mage.server.Main"),
}

data class XMageProcess(val role: XMageRole, val process: Process, val log: ProcessLog)

class XMageRunner(private val paths: Paths) {

    private val _server = MutableStateFlow<XMageProcess?>(null)
    val server: StateFlow<XMageProcess?> = _server

    private val _clients = MutableStateFlow<List<XMageProcess>>(emptyList())
    val clients: StateFlow<List<XMageProcess>> = _clients

    fun launchClient(settings: UserSettings, javaHome: Path): XMageProcess? {
        val proc = launch(XMageRole.Client, javaHome, settings.clientJvmOpts, extraArgs = emptyList())
        if (proc != null) _clients.value = _clients.value + proc
        return proc
    }

    fun launchServer(settings: UserSettings, javaHome: Path): XMageProcess? {
        if (_server.value?.process?.isAlive == true) {
            AppLog.i("Server already running (pid=${_server.value?.process?.pid()}); reusing it")
            return _server.value
        }
        val args = if (settings.serverTestMode) listOf("-testMode=true") else emptyList()
        val proc = launch(XMageRole.Server, javaHome, settings.serverJvmOpts, args)
        if (proc != null) _server.value = proc
        return proc
    }

    fun stopServer() {
        _server.value?.let { AppLog.i("Stopping server (pid=${it.process.pid()})") }
        _server.value?.process?.destroy()
        _server.value = null
    }

    private fun launch(role: XMageRole, javaHome: Path, jvmOpts: String, extraArgs: List<String>): XMageProcess? {
        val javaBin = paths.javaExecutable(javaHome)
        val rolePath = paths.xmageRoot.resolve(role.subdir)
        AppLog.i("Launching ${role.name}: javaHome=$javaHome, workDir=$rolePath")

        // Precondition checks — each was previously a silent `return null`, the root cause of
        // "the server just doesn't start with no error". Now every failure names the exact path.
        if (!Files.exists(javaBin)) {
            AppLog.e("Cannot launch ${role.name}: java executable not found at $javaBin")
            return null
        }
        if (!Files.isDirectory(rolePath)) {
            AppLog.e("Cannot launch ${role.name}: XMage directory missing at $rolePath (is XMage installed?)")
            return null
        }
        val libDir = rolePath.resolve("lib")
        if (!Files.isDirectory(libDir)) {
            AppLog.e("Cannot launch ${role.name}: classpath lib directory missing at $libDir")
            return null
        }

        val classPath = libDir.toString() + java.io.File.separator + "*"
        val cmd = buildList {
            add(javaBin.toString())
            addAll(jvmOpts.trim().split(Regex("\\s+")).filter { it.isNotEmpty() })
            add("-cp"); add(classPath)
            add(role.mainClass)
            addAll(extraArgs)
        }
        AppLog.d("${role.name} command: ${cmd.joinToString(" ")}")

        val pb = ProcessBuilder(cmd)
            .directory(rolePath.toFile())
            .redirectErrorStream(true)
        pb.environment()["JAVA_HOME"] = javaHome.toString()

        val process = runCatching { pb.start() }.getOrElse { t ->
            AppLog.e("Failed to start ${role.name} process", t)
            return null
        }
        AppLog.i("${role.name} started (pid=${process.pid()})")
        val log = ProcessLog.attach(process)
        // When the process exits, drop it from tracking.
        process.onExit().thenRun {
            AppLog.i("${role.name} exited (pid=${process.pid()}, code=${process.exitValue()})")
            when (role) {
                XMageRole.Client -> _clients.value = _clients.value.filter { it.process !== process }
                XMageRole.Server -> if (_server.value?.process === process) _server.value = null
            }
        }
        return XMageProcess(role, process, log)
    }
}
