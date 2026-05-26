package ie.stu.invoker.install

import ie.stu.invoker.platform.Os
import ie.stu.invoker.platform.PlatformInfo
import java.nio.file.Path
import kotlin.io.path.Path

class Paths(val installRoot: Path, private val platform: PlatformInfo) {
    val javaRoot: Path = installRoot.resolve("java")
    val xmageRoot: Path = installRoot.resolve("xmage")
    val installedProperties: Path = installRoot.resolve("installed.properties")
    val logFile: Path = installRoot.resolve("xmage_launcher.log")

    fun javaHomeFor(version: String): Path {
        val cleanVersion = version.replace("[^A-Za-z0-9._-]".toRegex(), "_")
        val base = javaRoot.resolve("jre$cleanVersion")
        return if (platform.os == Os.Mac) base.resolveSibling("jre$cleanVersion.jre").resolve("Contents/Home") else base
    }

    fun javaExecutable(javaHome: Path): Path {
        val bin = javaHome.resolve("bin")
        return if (platform.os == Os.Windows) bin.resolve("java.exe") else bin.resolve("java")
    }

    companion object {
        fun defaultInstallRoot(platform: PlatformInfo): Path {
            val home = System.getProperty("user.home")
            return when (platform.os) {
                Os.Windows -> {
                    val localAppData = System.getenv("LOCALAPPDATA")
                    if (!localAppData.isNullOrBlank()) Path(localAppData, "Invoker")
                    else Path(home, "AppData", "Local", "Invoker")
                }
                Os.Mac -> Path(home, "Library", "Application Support", "Invoker")
                Os.Linux -> {
                    val xdg = System.getenv("XDG_DATA_HOME")
                    if (!xdg.isNullOrBlank()) Path(xdg, "Invoker") else Path(home, ".local", "share", "Invoker")
                }
            }
        }
    }
}
