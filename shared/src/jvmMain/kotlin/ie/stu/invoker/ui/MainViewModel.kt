package ie.stu.invoker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ie.stu.invoker.AppEnvironment
import ie.stu.invoker.config.RemoteConfig
import ie.stu.invoker.download.DownloadProgress
import ie.stu.invoker.install.ArchiveExtractor
import ie.stu.invoker.platform.DetectedJava
import ie.stu.invoker.process.XMageProcess
import ie.stu.invoker.settings.InstalledVersions
import ie.stu.invoker.settings.JavaSource
import ie.stu.invoker.settings.UserSettings
import ie.stu.invoker.update.UpdatePlan
import ie.stu.invoker.update.UpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

enum class InstallStatus {
    NotInstalled,
    UpToDate,
    UpdateAvailable,
    Offline,
    Loading,
}

data class UiState(
    val settings: UserSettings = UserSettings(),
    val installed: InstalledVersions = InstalledVersions(),
    val remote: RemoteConfig? = null,
    val plan: UpdatePlan? = null,
    val busy: Boolean = false,
    val progress: DownloadProgress = DownloadProgress(0, null),
    val progressLabel: String? = null,
    val destination: Destination = Destination.Home,
    val serverRunning: Boolean = false,
    val clients: List<XMageProcess> = emptyList(),
    val server: XMageProcess? = null,
    val status: InstallStatus = InstallStatus.Loading,
    val detectedJavas: List<DetectedJava> = emptyList(),
    val javaDetectionRunning: Boolean = false,
) {
    val canLaunch: Boolean = installed.xmageVersion != null && when (settings.javaSource) {
        is JavaSource.Custom -> true
        JavaSource.Bundled -> installed.javaVersion != null
    }
    val updateVersion: String? = remote?.xmage?.version?.takeIf { plan?.needsXMage == true || plan?.needsJava == true }
}

class MainViewModel(private val env: AppEnvironment) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        val (settings, installed) = env.installedState.load()
        _state.value = _state.value.copy(settings = settings, installed = installed)

        viewModelScope.launch {
            env.downloader.progress.collectLatest { _state.value = _state.value.copy(progress = it) }
        }
        viewModelScope.launch {
            env.runner.server.collectLatest { srv ->
                _state.value = _state.value.copy(server = srv, serverRunning = srv?.process?.isAlive == true)
            }
        }
        viewModelScope.launch {
            env.runner.clients.collectLatest { _state.value = _state.value.copy(clients = it) }
        }

        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh(): Boolean {
        return try {
            val remote = env.configService.fetch(_state.value.settings.xmageHomeUrl)
            // Only the bundled-JRE source benefits from auto-downloading Java; for custom paths
            // the user owns the runtime, so we skip Java update checks.
            val skipJava = _state.value.settings.javaSource !is ie.stu.invoker.settings.JavaSource.Bundled
            val plan = UpdateService.plan(remote, _state.value.installed, skipJava)
            _state.value = _state.value.copy(remote = remote, plan = plan, status = installStatusFor(plan))
            true
        } catch (_: Exception) {
            _state.value = _state.value.copy(status = InstallStatus.Offline)
            false
        }
    }

    private fun installStatusFor(plan: UpdatePlan): InstallStatus = when {
        _state.value.installed.xmageVersion == null -> InstallStatus.NotInstalled
        plan.anything -> InstallStatus.UpdateAvailable
        else -> InstallStatus.UpToDate
    }

    fun checkUpdates() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(status = InstallStatus.Loading)
            val ok = refresh()
            val msg = when {
                !ok -> Strings.SNACKBAR_CONFIG_ERROR
                _state.value.plan?.anything == true ->
                    String.format(Strings.SNACKBAR_UPDATE_AVAILABLE, _state.value.remote?.xmage?.version)
                else -> Strings.SNACKBAR_LATEST
            }
            _messages.emit(msg)
        }
    }

    fun runUpdates() {
        val plan = _state.value.plan ?: return
        val remote = _state.value.remote ?: return
        if (_state.value.busy || !plan.anything) return

        // Legacy XMage Launcher issue #33: extracting XMage while a client/server is alive
        // corrupts the running process's classpath. Refuse and prompt the user to close them.
        val running = _state.value.clients.count { it.process.isAlive } +
            if (_state.value.server?.process?.isAlive == true) 1 else 0
        if (running > 0) {
            viewModelScope.launch {
                val msg = if (running == 1) Strings.SNACKBAR_RUNNING_SINGULAR
                else String.format(Strings.SNACKBAR_RUNNING_PLURAL, running)
                _messages.emit(msg)
            }
            return
        }

        val firstInstall = _state.value.installed.xmageVersion == null
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                if (plan.needsJava) downloadJava(remote)
                if (plan.needsXMage) downloadXMage(remote)
                refresh()
                val template = if (firstInstall) Strings.SNACKBAR_INSTALLED else Strings.SNACKBAR_UPDATED
                _messages.emit(String.format(template, remote.xmage.version))
            } catch (e: Exception) {
                val template = if (firstInstall) Strings.SNACKBAR_INSTALL_ERROR else Strings.SNACKBAR_UPDATE_ERROR
                _messages.emit(String.format(template, e.message ?: "unknown error"))
            } finally {
                _state.value = _state.value.copy(busy = false, progressLabel = null)
            }
        }
    }

    private suspend fun downloadJava(remote: RemoteConfig) {
        val info = remote.java
        val url = info.location + env.platform.javaUrlSuffix
        _state.value = _state.value.copy(progressLabel = Strings.PROGRESS_DOWNLOADING_JAVA)
        val ext = env.platform.javaArchiveExtension
        val tmp = env.paths.installRoot.resolve("java-download.$ext")
        withContext(Dispatchers.IO) {
            Files.createDirectories(env.paths.installRoot)
            env.downloader.download(primary = url, destination = tmp)
            _state.value = _state.value.copy(progressLabel = Strings.PROGRESS_INSTALLING_JAVA)
            val target = env.paths.javaHomeFor(info.version).let { resolveJavaUnpackDir(it) }
            Files.createDirectories(target)
            if (ext == "zip") ArchiveExtractor.extractZip(tmp, target)
            else ArchiveExtractor.extractTarGz(tmp, target)
            Files.deleteIfExists(tmp)
        }
        val (settings, versions) = env.installedState.load()
        env.installedState.save(settings, versions.copy(javaVersion = info.version))
        _state.value = _state.value.copy(installed = _state.value.installed.copy(javaVersion = info.version))
    }

    private suspend fun downloadXMage(remote: RemoteConfig) {
        val info = remote.xmage
        _state.value = _state.value.copy(progressLabel = Strings.PROGRESS_DOWNLOADING_XMAGE)
        val tmp = env.paths.installRoot.resolve("xmage-download.zip")
        withContext(Dispatchers.IO) {
            Files.createDirectories(env.paths.installRoot)
            env.downloader.download(primary = info.location, mirrors = info.locations, destination = tmp)
            _state.value = _state.value.copy(progressLabel = Strings.PROGRESS_INSTALLING_XMAGE)
            Files.createDirectories(env.paths.xmageRoot)
            ArchiveExtractor.extractXMageZip(tmp, env.paths.xmageRoot)
            Files.deleteIfExists(tmp)
        }
        val (settings, versions) = env.installedState.load()
        env.installedState.save(settings, versions.copy(xmageVersion = info.version))
        _state.value = _state.value.copy(installed = _state.value.installed.copy(xmageVersion = info.version))
    }

    /** macOS uses jre{version}.jre/Contents/Home — unpack one level above .../Contents. */
    private fun resolveJavaUnpackDir(javaHome: Path): Path {
        var p: Path = javaHome
        while (p.fileName?.toString() == "Home" || p.fileName?.toString() == "Contents") {
            p = p.parent ?: break
        }
        return p.parent ?: javaHome
    }

    fun launchClient() {
        val javaHome = resolveJavaHome() ?: return
        env.runner.launchClient(_state.value.settings, javaHome)
    }

    fun launchServer() {
        val javaHome = resolveJavaHome() ?: return
        env.runner.launchServer(_state.value.settings, javaHome)
    }

    fun launchClientAndServer() {
        launchServer()
        launchClient()
    }

    fun stopServer() = env.runner.stopServer()

    private fun resolveJavaHome(): Path? {
        return when (val src = _state.value.settings.javaSource) {
            is JavaSource.Custom -> Path.of(src.path)
            JavaSource.Bundled -> {
                val version = _state.value.installed.javaVersion ?: return null
                env.paths.javaHomeFor(version)
            }
        }
    }

    /** Kick off a fresh scan of the user's machine. Safe to call repeatedly. */
    fun refreshJavaDetection() {
        if (_state.value.javaDetectionRunning) return
        viewModelScope.launch {
            _state.value = _state.value.copy(javaDetectionRunning = true)
            val found = runCatching { env.javaDetector.detect() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(detectedJavas = found, javaDetectionRunning = false)
        }
    }

    /** Validate a user-provided path. Result.isSuccess implies the JRE is usable for XMage. */
    suspend fun validateJavaPath(path: Path): Result<DetectedJava> = env.javaDetector.validate(path)


    fun navigateTo(destination: Destination) {
        _state.value = _state.value.copy(destination = destination)
    }

    fun applySettings(updated: UserSettings) {
        env.installedState.save(updated, _state.value.installed)
        _state.value = _state.value.copy(settings = updated)
        viewModelScope.launch { refresh() }
    }
}
