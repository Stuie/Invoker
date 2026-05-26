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
        if (_server.value?.process?.isAlive == true) return _server.value
        val args = if (settings.serverTestMode) listOf("-testMode=true") else emptyList()
        val proc = launch(XMageRole.Server, javaHome, settings.serverJvmOpts, args)
        if (proc != null) _server.value = proc
        return proc
    }

    fun stopServer() {
        _server.value?.process?.destroy()
        _server.value = null
    }

    private fun launch(role: XMageRole, javaHome: Path, jvmOpts: String, extraArgs: List<String>): XMageProcess? {
        val javaBin = paths.javaExecutable(javaHome)
        val rolePath = paths.xmageRoot.resolve(role.subdir)
        if (!Files.exists(javaBin) || !Files.isDirectory(rolePath)) return null

        val classPath = rolePath.resolve("lib").toString() + java.io.File.separator + "*"
        val cmd = buildList {
            add(javaBin.toString())
            addAll(jvmOpts.trim().split(Regex("\\s+")).filter { it.isNotEmpty() })
            add("-cp"); add(classPath)
            add(role.mainClass)
            addAll(extraArgs)
        }

        val pb = ProcessBuilder(cmd)
            .directory(rolePath.toFile())
            .redirectErrorStream(true)
        pb.environment()["JAVA_HOME"] = javaHome.toString()

        val process = runCatching { pb.start() }.getOrNull() ?: return null
        val log = ProcessLog.attach(process)
        // When the process exits, drop it from tracking.
        process.onExit().thenRun {
            when (role) {
                XMageRole.Client -> _clients.value = _clients.value.filter { it.process !== process }
                XMageRole.Server -> if (_server.value?.process === process) _server.value = null
            }
        }
        return XMageProcess(role, process, log)
    }
}
