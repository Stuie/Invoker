package ie.stu.invoker.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * A working Java installation discovered on the user's machine. The home is the canonical
 * `java.home` value reported by the JRE itself (which may differ from a user-supplied path
 * — e.g. macOS bundles, or a JDK whose JRE lives in `jdk/jre/`).
 *
 * Note: no `displayName` here. Formatting the version + vendor into a user-visible string
 * happens in the UI layer (see `ui.controls.javaDisplayName`) so this layer stays free of
 * locale-bound copy.
 */
data class DetectedJava(
    val home: Path,
    val executable: Path,
    val rawVersion: String,
    val majorVersion: Int,
    val vendor: String?,
    val source: DetectionSource,
)

enum class DetectionSource { JavaHomeEnv, KnownLocation, UserProvided }

/** Typed failure reasons that the UI converts into localised messages via Strings. */
sealed class JavaValidationError(message: String) : RuntimeException(message) {
    /** No bin/java(.exe) under the chosen path. The expected exe name is platform-dependent. */
    class NotAValidJava(val expectedExeName: String) : JavaValidationError("Not a valid Java installation: bin/$expectedExeName not found")
}

class JavaDetector(private val platform: PlatformInfo) {

    /** Set of homes to omit from results — at minimum the JRE Invoker is currently running on. */
    private val excludedHomes: Set<Path> = setOfNotNull(
        runCatching { Path(System.getProperty("java.home")).toAbsolutePath().normalize() }.getOrNull(),
    )

    /** Scan platform-typical install locations and JAVA_HOME. Results are de-duplicated by canonical home. */
    suspend fun detect(): List<DetectedJava> = withContext(Dispatchers.IO) {
        val seen = mutableSetOf<Path>()
        val results = mutableListOf<DetectedJava>()

        System.getenv("JAVA_HOME")?.let { jh ->
            validateOrNull(Path(jh), DetectionSource.JavaHomeEnv)?.let { results.add(it) }
        }
        for (root in knownInstallRoots()) {
            if (!root.exists() || !root.isDirectory()) continue
            val children = runCatching { root.listDirectoryEntries() }.getOrNull() ?: continue
            for (child in children) {
                val candidate = resolveMacBundle(child)
                validateOrNull(candidate, DetectionSource.KnownLocation)?.let { results.add(it) }
            }
        }
        results
            .filter { it.home !in excludedHomes }
            .distinctBy { it.home }
    }

    /**
     * Validate a user-provided path. Tolerates the user pointing at a JDK root, a JRE root,
     * a macOS .jre bundle, or a `bin` directory. Returns a Result that the UI can show.
     */
    suspend fun validate(userPath: Path): Result<DetectedJava> = withContext(Dispatchers.IO) {
        runCatching {
            val candidate = resolveMacBundle(userPath.toAbsolutePath().normalize())
            validateOrNull(candidate, DetectionSource.UserProvided)
                ?: throw JavaValidationError.NotAValidJava(javaExe())
        }
    }

    private fun knownInstallRoots(): List<Path> = when (platform.os) {
        Os.Windows -> listOf(
            Path("C:\\Program Files\\Eclipse Adoptium"),
            Path("C:\\Program Files\\Eclipse Foundation"),
            Path("C:\\Program Files\\AdoptOpenJDK"),
            Path("C:\\Program Files\\Java"),
            Path("C:\\Program Files (x86)\\Java"),
            Path("C:\\Program Files\\Amazon Corretto"),
            Path("C:\\Program Files\\Microsoft"),
            Path("C:\\Program Files\\Zulu"),
            Path("C:\\Program Files\\BellSoft"),
            Path("C:\\Program Files\\SapMachine"),
        )
        Os.Mac -> listOf(
            Path("/Library/Java/JavaVirtualMachines"),
            Path("/System/Library/Java/JavaVirtualMachines"),
            Path(System.getProperty("user.home"), "Library", "Java", "JavaVirtualMachines"),
            Path(System.getProperty("user.home"), ".sdkman", "candidates", "java"),
        )
        Os.Linux -> listOf(
            Path("/usr/lib/jvm"),
            Path("/usr/lib64/jvm"),
            Path("/opt/java"),
            Path("/opt/jdk"),
            Path(System.getProperty("user.home"), ".sdkman", "candidates", "java"),
            Path(System.getProperty("user.home"), ".jenv", "versions"),
        )
    }

    /** On macOS, a .jre or .jdk bundle's java lives at `Contents/Home/bin/java`. */
    private fun resolveMacBundle(path: Path): Path {
        if (platform.os != Os.Mac) return path
        val home = path.resolve("Contents/Home")
        return if (home.exists()) home else path
    }

    private fun javaExe(): String = if (platform.os == Os.Windows) "java.exe" else "java"

    private fun validateOrNull(userPath: Path, source: DetectionSource): DetectedJava? {
        val home = locateHome(userPath) ?: return null
        val exe = home.resolve("bin").resolve(javaExe())
        if (!Files.isExecutable(exe)) return null
        val props = runProperties(exe) ?: return null
        val canonicalHome = props["java.home"]?.let { Path(it).toAbsolutePath().normalize() } ?: home.toAbsolutePath().normalize()
        val version = props["java.version"] ?: return null
        val vendor = props["java.vendor"] ?: props["java.vendor.name"]
        val major = parseMajorVersion(version) ?: return null
        return DetectedJava(
            home = canonicalHome,
            executable = exe,
            rawVersion = version,
            majorVersion = major,
            vendor = vendor,
            source = source,
        )
    }

    /** Accept the user pointing at the home, the JDK root, or the bin directory. */
    private fun locateHome(userPath: Path): Path? {
        val exeName = javaExe()
        if (userPath.resolve("bin").resolve(exeName).let { Files.isExecutable(it) }) return userPath
        if (userPath.fileName?.toString() == "bin" && Files.isExecutable(userPath.resolve(exeName))) return userPath.parent
        // JDKs sometimes have a nested `jre/` with the public JRE
        val jre = userPath.resolve("jre")
        if (jre.resolve("bin").resolve(exeName).let { Files.isExecutable(it) }) return jre
        return null
    }

    private fun runProperties(exe: Path): Map<String, String>? = runCatching {
        val pb = ProcessBuilder(exe.toString(), "-XshowSettings:properties", "-version")
            .redirectErrorStream(true)
        val process = pb.start()
        val text = BufferedReader(InputStreamReader(process.inputStream)).readText()
        process.waitFor()
        if (process.exitValue() != 0) return@runCatching null
        // -XshowSettings prints "  key = value" lines under a "Property settings:" section.
        val map = mutableMapOf<String, String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            if (key.isNotEmpty() && key.matches(Regex("[a-zA-Z][\\w.]+"))) map[key] = value
        }
        map
    }.getOrNull()

    /**
     * Parse "1.8.0_201" → 8, "11.0.5" → 11, "17.0.1" → 17, "21+35-LTS" → 21.
     * Marked internal so it can be exercised by tests.
     */
    internal fun parseMajorVersion(version: String): Int? {
        val parts = version.split('.', '_', '-', '+')
        val first = parts.firstOrNull()?.toIntOrNull() ?: return null
        return if (first == 1 && parts.size > 1) parts[1].toIntOrNull() else first
    }
}
