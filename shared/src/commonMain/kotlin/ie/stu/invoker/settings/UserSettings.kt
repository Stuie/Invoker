package ie.stu.invoker.settings

import ie.stu.invoker.decks.ImageQuality

/**
 * Which Java runtime the launcher uses to spawn XMage. Note: this is **not** the launcher's
 * own runtime (that's bundled by jpackage at build time); this is the one passed to
 * `mage.client.MageFrame` / `mage.server.Main` as `JAVA_HOME`. XMage expects Java 8.
 */
sealed interface JavaSource {
    /** Java 8 JRE downloaded by Invoker from the configured XMage home. Default. */
    data object Bundled : JavaSource

    /** A user-provided JRE/JDK directory. Path is the resolved java.home. */
    data class Custom(val path: String) : JavaSource
}

data class UserSettings(
    val xmageBranch: Branch = Branch.Main,
    val xmageHomeUrl: String = "https://xmage.today",
    val javaSource: JavaSource = JavaSource.Bundled,
    val clientJvmOpts: String = DEFAULT_CLIENT_JVM_OPTS,
    val serverJvmOpts: String = DEFAULT_SERVER_JVM_OPTS,
    val guiSize: Int = 100,
    val clientStartupDelaySeconds: Int = 0,
    val showClientConsole: Boolean = false,
    val showServerConsole: Boolean = false,
    val serverTestMode: Boolean = false,
    val deckImageQuality: ImageQuality = ImageQuality.Large,
) {
    enum class Branch { Main, Custom }

    companion object {
        const val DEFAULT_CLIENT_JVM_OPTS =
            "-Xmx2000m -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Djava.net.preferIPv4Stack=true"
        const val DEFAULT_SERVER_JVM_OPTS = "-Xmx1000m"
        const val MAIN_BRANCH_URL = "https://xmage.today"
    }
}

data class InstalledVersions(
    val xmageVersion: String? = null,
    val javaVersion: String? = null,
)
