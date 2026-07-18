package ie.stu.invoker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ie.stu.invoker.AppEnvironment
import ie.stu.invoker.config.RemoteConfig
import ie.stu.invoker.decks.CardResult
import ie.stu.invoker.decks.DeckImportResult
import ie.stu.invoker.decks.DeckParsers
import ie.stu.invoker.decks.ImageQuality
import ie.stu.invoker.download.DownloadProgress
import ie.stu.invoker.install.ArchiveExtractor
import ie.stu.invoker.platform.DetectedJava
import ie.stu.invoker.process.AppLog
import ie.stu.invoker.process.XMageProcess
import ie.stu.invoker.settings.InstalledVersions
import ie.stu.invoker.settings.JavaSource
import ie.stu.invoker.settings.UserSettings
import ie.stu.invoker.update.UpdatePlan
import ie.stu.invoker.update.UpdateService
import io.ktor.client.plugins.ResponseException
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

/**
 * Self-contained state for the Decks pane. Kept separate from the top-level download [progress] so
 * a deck sync and an XMage install never clobber each other's progress.
 */
data class DeckSyncState(
    val rawInput: String = "",
    val running: Boolean = false,
    val importing: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val results: List<CardResult> = emptyList(),
    val ignoredLines: Int = 0,
    val hint: String? = null,
    val error: String? = null,
)

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
    val deckSync: DeckSyncState = DeckSyncState(),
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
            val skipJava = _state.value.settings.javaSource !is JavaSource.Bundled
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
        if (env.runner.launchClient(_state.value.settings, javaHome) == null) {
            failLaunch(Strings.SNACKBAR_CLIENT_LAUNCH_FAILED)
        }
    }

    fun launchServer() {
        val javaHome = resolveJavaHome() ?: return
        if (env.runner.launchServer(_state.value.settings, javaHome) == null) {
            failLaunch(Strings.SNACKBAR_SERVER_LAUNCH_FAILED)
        }
    }

    fun launchClientAndServer() {
        launchServer()
        launchClient()
    }

    fun stopServer() = env.runner.stopServer()

    /** Log the failure context and nudge the user toward the F3 debug overlay for the details. */
    private fun failLaunch(message: String) {
        AppLog.w("Launch failed — see above for the reason (press F3 for the debug overlay)")
        viewModelScope.launch { _messages.emit(message) }
    }

    private fun resolveJavaHome(): Path? {
        return when (val src = _state.value.settings.javaSource) {
            is JavaSource.Custom -> Path.of(src.path)
            JavaSource.Bundled -> {
                val version = _state.value.installed.javaVersion
                if (version == null) {
                    AppLog.e(
                        "Cannot resolve Java home: source is Bundled but no Java version is installed. " +
                            "Install XMage (which downloads the bundled JRE) or set a custom Java path in Settings.",
                    )
                    failLaunch(Strings.SNACKBAR_NO_JAVA)
                    return null
                }
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

    // ── Decks ────────────────────────────────────────────────────────────────

    private fun updateDeckSync(transform: (DeckSyncState) -> DeckSyncState) {
        _state.value = _state.value.copy(deckSync = transform(_state.value.deckSync))
    }

    fun onDeckInputChanged(text: String) {
        updateDeckSync { it.copy(rawInput = text, hint = null, error = null) }
    }

    /** Persist the image-quality choice immediately. No config refresh — it's a local preference. */
    fun setDeckImageQuality(quality: ImageQuality) {
        val updated = _state.value.settings.copy(deckImageQuality = quality)
        env.installedState.save(updated, _state.value.installed)
        _state.value = _state.value.copy(settings = updated)
    }

    /** Read an XMage `.dck` (or any text deck) from disk into the paste field for review. */
    fun loadDeckFile(path: Path) {
        viewModelScope.launch {
            val text = runCatching { withContext(Dispatchers.IO) { Files.readString(path) } }.getOrNull()
            if (text != null) onDeckInputChanged(text)
            else _messages.emit(String.format(Strings.SNACKBAR_DECK_IMPORT_ERROR, "couldn't read the file"))
        }
    }

    fun importFromUrl(url: String) {
        val ds = _state.value.deckSync
        if (url.isBlank() || ds.importing || ds.running) return
        viewModelScope.launch {
            updateDeckSync { it.copy(importing = true, hint = null, error = null) }
            when (val result = env.deckUrlImporter.fetch(url)) {
                is DeckImportResult.Success -> updateDeckSync {
                    it.copy(
                        importing = false,
                        rawInput = entriesToText(result.entries),
                        hint = String.format(Strings.DECKS_IMPORTED, result.entries.size),
                    )
                }
                is DeckImportResult.Unsupported -> updateDeckSync {
                    it.copy(importing = false, hint = String.format(Strings.DECKS_HINT_UNSUPPORTED_URL, result.host))
                }
                is DeckImportResult.Failed -> {
                    updateDeckSync { it.copy(importing = false, error = result.message) }
                    _messages.emit(String.format(Strings.SNACKBAR_DECK_IMPORT_ERROR, result.message))
                }
            }
        }
    }

    fun syncDeck() {
        val ds = _state.value.deckSync
        if (ds.running || ds.rawInput.isBlank()) return
        val parsed = DeckParsers.parse(ds.rawInput)
        if (parsed.entries.isEmpty()) {
            updateDeckSync { it.copy(hint = Strings.DECKS_HINT_EMPTY) }
            return
        }
        val quality = _state.value.settings.deckImageQuality
        viewModelScope.launch {
            updateDeckSync {
                it.copy(
                    running = true,
                    completed = 0,
                    total = parsed.entries.size,
                    results = emptyList(),
                    ignoredLines = parsed.ignoredLines.size,
                    hint = null,
                    error = null,
                )
            }
            try {
                env.cardImageSyncService.sync(parsed.entries, quality).collect { p ->
                    updateDeckSync {
                        it.copy(completed = p.completed, total = p.total, results = p.results, running = !p.done)
                    }
                }
            } catch (e: Exception) {
                val friendly = friendlyScryfallError(e)
                updateDeckSync { it.copy(running = false, error = friendly) }
                _messages.emit(String.format(Strings.SNACKBAR_DECK_SYNC_ERROR, friendly))
            }
        }
    }

    /**
     * Turns a Scryfall failure into a user-facing message. Ktor's exception message for a non-2xx
     * response embeds the raw JSON error body — we never want that on screen, so 429s and other HTTP
     * errors are mapped to plain sentences.
     */
    private fun friendlyScryfallError(e: Throwable): String = when {
        e is ResponseException && e.response.status.value == 429 -> Strings.DECKS_ERROR_RATE_LIMITED
        e is ResponseException -> String.format(Strings.DECKS_ERROR_HTTP, e.response.status.value)
        else -> Strings.DECKS_ERROR_GENERIC
    }

    /** Serialise imported entries back to a text deck the user can see and edit before syncing. */
    private fun entriesToText(entries: List<ie.stu.invoker.decks.DeckEntry>): String {
        fun line(e: ie.stu.invoker.decks.DeckEntry): String = buildString {
            append(e.count).append(' ').append(e.name)
            if (e.setCode != null) {
                append(" (").append(e.setCode).append(')')
                if (e.collectorNumber != null) append(' ').append(e.collectorNumber)
            }
        }
        val main = entries.filter { !it.sideboard }
        val sideboard = entries.filter { it.sideboard }
        return buildString {
            main.forEach { appendLine(line(it)) }
            if (sideboard.isNotEmpty()) {
                appendLine()
                appendLine("Sideboard")
                sideboard.forEach { appendLine(line(it)) }
            }
        }.trim()
    }
}
