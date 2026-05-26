package ie.stu.invoker.install

import ie.stu.invoker.settings.InstalledVersions
import ie.stu.invoker.settings.JavaSource
import ie.stu.invoker.settings.UserSettings
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

private object Keys {
    const val XMAGE_VERSION = "xmage.version"
    const val JAVA_VERSION = "java.version"
    const val JAVA_SOURCE = "java.source"          // "bundled" | "custom"
    const val JAVA_SOURCE_PATH = "java.source.path"
    const val LEGACY_USE_SYSTEM_JAVA = "useSystemJava" // migrated; no longer written
    const val CLIENT_JVM_OPTS = "client.jvm.opts"
    const val SERVER_JVM_OPTS = "server.jvm.opts"
    const val BRANCH = "xmage.branch"
    const val HOME_URL = "xmage.home.url"
    const val GUI_SIZE = "gui.size"
    const val CLIENT_STARTUP_DELAY = "client.startup.delay"
    const val SHOW_CLIENT_CONSOLE = "client.console.show"
    const val SHOW_SERVER_CONSOLE = "server.console.show"
    const val SERVER_TEST_MODE = "server.test.mode"
}

class InstalledState(private val file: Path) {

    fun load(): Pair<UserSettings, InstalledVersions> {
        if (!Files.exists(file)) return UserSettings() to InstalledVersions()
        // Single Properties object — if Properties.load throws partway through, the puts that
        // happened before the bad line remain. Reusing the partial state lets us preserve
        // branch + home URL even on corruption.
        val props = Properties()
        val loadResult = runCatching { Files.newInputStream(file).use { props.load(it) } }
        return if (loadResult.isSuccess) {
            settingsFrom(props) to versionsFrom(props)
        } else {
            val preservedHome = props.getProperty(Keys.HOME_URL) ?: UserSettings.MAIN_BRANCH_URL
            val preservedBranch = props.getProperty(Keys.BRANCH)?.let { name ->
                runCatching { UserSettings.Branch.valueOf(name) }.getOrNull()
            } ?: UserSettings.Branch.Main
            UserSettings(xmageBranch = preservedBranch, xmageHomeUrl = preservedHome) to InstalledVersions()
        }
    }

    fun save(settings: UserSettings, versions: InstalledVersions) {
        val props = Properties()
        versions.xmageVersion?.let { props.setProperty(Keys.XMAGE_VERSION, it) }
        versions.javaVersion?.let { props.setProperty(Keys.JAVA_VERSION, it) }
        when (val src = settings.javaSource) {
            JavaSource.Bundled -> props.setProperty(Keys.JAVA_SOURCE, "bundled")
            is JavaSource.Custom -> {
                props.setProperty(Keys.JAVA_SOURCE, "custom")
                props.setProperty(Keys.JAVA_SOURCE_PATH, src.path)
            }
        }
        props.setProperty(Keys.CLIENT_JVM_OPTS, settings.clientJvmOpts)
        props.setProperty(Keys.SERVER_JVM_OPTS, settings.serverJvmOpts)
        props.setProperty(Keys.BRANCH, settings.xmageBranch.name)
        props.setProperty(Keys.HOME_URL, settings.xmageHomeUrl)
        props.setProperty(Keys.GUI_SIZE, settings.guiSize.toString())
        props.setProperty(Keys.CLIENT_STARTUP_DELAY, settings.clientStartupDelaySeconds.toString())
        props.setProperty(Keys.SHOW_CLIENT_CONSOLE, settings.showClientConsole.toString())
        props.setProperty(Keys.SHOW_SERVER_CONSOLE, settings.showServerConsole.toString())
        props.setProperty(Keys.SERVER_TEST_MODE, settings.serverTestMode.toString())
        Files.createDirectories(file.parent)
        Files.newOutputStream(file).use { props.store(it, "Invoker settings") }
    }

    private fun settingsFrom(props: Properties): UserSettings {
        val defaults = UserSettings()
        val branch = props.getProperty(Keys.BRANCH)?.let {
            runCatching { UserSettings.Branch.valueOf(it) }.getOrNull()
        } ?: defaults.xmageBranch
        return UserSettings(
            xmageBranch = branch,
            xmageHomeUrl = props.getProperty(Keys.HOME_URL) ?: defaults.xmageHomeUrl,
            javaSource = readJavaSource(props),
            clientJvmOpts = props.getProperty(Keys.CLIENT_JVM_OPTS) ?: defaults.clientJvmOpts,
            serverJvmOpts = props.getProperty(Keys.SERVER_JVM_OPTS) ?: defaults.serverJvmOpts,
            guiSize = props.getProperty(Keys.GUI_SIZE)?.toIntOrNull() ?: defaults.guiSize,
            clientStartupDelaySeconds = props.getProperty(Keys.CLIENT_STARTUP_DELAY)?.toIntOrNull() ?: defaults.clientStartupDelaySeconds,
            showClientConsole = props.getProperty(Keys.SHOW_CLIENT_CONSOLE)?.toBooleanStrictOrNull() ?: defaults.showClientConsole,
            showServerConsole = props.getProperty(Keys.SHOW_SERVER_CONSOLE)?.toBooleanStrictOrNull() ?: defaults.showServerConsole,
            serverTestMode = props.getProperty(Keys.SERVER_TEST_MODE)?.toBooleanStrictOrNull() ?: defaults.serverTestMode,
        )
    }

    /**
     * Reads the JavaSource. New keys win; legacy `useSystemJava=true` migrates to Bundled
     * (the old path resolved to the launcher's own runtime in packaged builds, which was
     * misleading — Bundled is the safe / predictable default).
     */
    private fun readJavaSource(props: Properties): JavaSource {
        val newKey = props.getProperty(Keys.JAVA_SOURCE)
        if (newKey != null) {
            return when (newKey) {
                "custom" -> {
                    val path = props.getProperty(Keys.JAVA_SOURCE_PATH)
                    if (!path.isNullOrBlank()) JavaSource.Custom(path) else JavaSource.Bundled
                }
                else -> JavaSource.Bundled
            }
        }
        // legacy migration: any prior useSystemJava value collapses to Bundled
        return JavaSource.Bundled
    }

    private fun versionsFrom(props: Properties): InstalledVersions = InstalledVersions(
        xmageVersion = props.getProperty(Keys.XMAGE_VERSION),
        javaVersion = props.getProperty(Keys.JAVA_VERSION),
    )
}
