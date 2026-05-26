package ie.stu.invoker.install

import ie.stu.invoker.settings.InstalledVersions
import ie.stu.invoker.settings.JavaSource
import ie.stu.invoker.settings.UserSettings
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InstalledStateTest {

    private lateinit var tmpDir: Path
    private lateinit var propsFile: Path
    private lateinit var state: InstalledState

    @BeforeTest
    fun setUp() {
        tmpDir = Files.createTempDirectory("invoker-test-")
        propsFile = tmpDir.resolve("installed.properties")
        state = InstalledState(propsFile)
    }

    @AfterTest
    fun tearDown() {
        Files.walk(tmpDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun `defaults when no file exists`() {
        val (settings, versions) = state.load()
        assertEquals(UserSettings(), settings)
        assertEquals(InstalledVersions(), versions)
    }

    @Test
    fun `save and load round-trips bundled JavaSource`() {
        val settings = UserSettings(
            javaSource = JavaSource.Bundled,
            clientJvmOpts = "-Xmx512m",
            showClientConsole = true,
            xmageBranch = UserSettings.Branch.Main,
        )
        val versions = InstalledVersions(xmageVersion = "1.4.55-dev", javaVersion = "1.8.0_201")

        state.save(settings, versions)
        val (loadedSettings, loadedVersions) = state.load()

        assertEquals(settings, loadedSettings)
        assertEquals(versions, loadedVersions)
    }

    @Test
    fun `save and load round-trips custom JavaSource path`() {
        val customPath = "C:\\Program Files\\Eclipse Adoptium\\jdk-8.0.362.9-hotspot"
        val settings = UserSettings(javaSource = JavaSource.Custom(customPath))

        state.save(settings, InstalledVersions())
        val (loaded, _) = state.load()

        val src = loaded.javaSource
        assertIs<JavaSource.Custom>(src)
        assertEquals(customPath, src.path)
    }

    @Test
    fun `corrupt file recovers to defaults but preserves branch and home URL`() {
        // Write a file that's recognizable as a Properties file but has values that survive
        // best-effort parsing — the corruption-recovery path reads it raw and keeps branch + url.
        propsFile.writeText("xmage.branch=Custom\nxmage.home.url=https://example.invalid/\nthis is not=valid\\u escape")

        // Force a true parse failure via an invalid \u escape (Properties is strict about these).
        val (settings, versions) = state.load()
        assertEquals(UserSettings.Branch.Custom, settings.xmageBranch)
        assertEquals("https://example.invalid/", settings.xmageHomeUrl)
        assertEquals(InstalledVersions(), versions)
    }

    @Test
    fun `legacy useSystemJava=true migrates to Bundled JavaSource`() {
        propsFile.writeText("useSystemJava=true\nxmage.branch=Main\nxmage.home.url=https://xmage.today\n")
        val (settings, _) = state.load()
        assertEquals(JavaSource.Bundled, settings.javaSource)
    }

    @Test
    fun `save then second save overwrites prior values`() {
        state.save(UserSettings(clientJvmOpts = "-Xmx100m"), InstalledVersions(xmageVersion = "1.0.0"))
        state.save(UserSettings(clientJvmOpts = "-Xmx200m"), InstalledVersions(xmageVersion = "2.0.0"))
        val (settings, versions) = state.load()
        assertEquals("-Xmx200m", settings.clientJvmOpts)
        assertEquals("2.0.0", versions.xmageVersion)
        assertTrue(Files.exists(propsFile))
    }
}
