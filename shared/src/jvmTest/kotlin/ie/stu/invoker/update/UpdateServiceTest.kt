package ie.stu.invoker.update

import ie.stu.invoker.config.JavaInfo
import ie.stu.invoker.config.LauncherInfo
import ie.stu.invoker.config.RemoteConfig
import ie.stu.invoker.config.XMageInfo
import ie.stu.invoker.settings.InstalledVersions
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateServiceTest {

    private fun remote(xmage: String, java: String = "1.8.0_201"): RemoteConfig = RemoteConfig(
        java = JavaInfo(version = java, location = "https://xmage.today/java/jre-"),
        xmage = XMageInfo(
            version = xmage,
            location = "https://xmage.today/files/mage-update.zip",
            launcher = LauncherInfo(version = "0.0.0", location = ""),
        ),
    )

    @Test
    fun `null installed XMage version triggers update`() {
        val plan = UpdateService.plan(remote("1.4.55-dev"), InstalledVersions(xmageVersion = null, javaVersion = null))
        assertTrue(plan.needsXMage)
        assertFalse(plan.downgradeXMage)
    }

    @Test
    fun `equal versions need nothing`() {
        val plan = UpdateService.plan(
            remote("1.4.55-dev"),
            InstalledVersions(xmageVersion = "1.4.55-dev", javaVersion = "1.8.0_201"),
        )
        assertFalse(plan.needsXMage)
        assertFalse(plan.needsJava)
        assertFalse(plan.downgradeXMage)
        assertFalse(plan.anything)
    }

    @Test
    fun `installed older than remote needs XMage update`() {
        val plan = UpdateService.plan(
            remote("1.4.56-dev"),
            InstalledVersions(xmageVersion = "1.4.55-dev", javaVersion = "1.8.0_201"),
        )
        assertTrue(plan.needsXMage)
        assertFalse(plan.downgradeXMage)
        assertTrue(plan.anything)
    }

    @Test
    fun `installed newer than remote flags downgrade, no update`() {
        val plan = UpdateService.plan(
            remote("1.4.55-dev"),
            InstalledVersions(xmageVersion = "1.4.56-dev", javaVersion = "1.8.0_201"),
        )
        assertFalse(plan.needsXMage)
        assertTrue(plan.downgradeXMage)
    }

    @Test
    fun `useSystemJava (skipJava) bypasses Java update check`() {
        val plan = UpdateService.plan(
            remote = remote("1.4.55-dev", java = "1.8.0_999"),
            installed = InstalledVersions(xmageVersion = "1.4.55-dev", javaVersion = null),
            useSystemJava = true,
        )
        assertFalse(plan.needsJava, "needsJava must be false when skipJava (system Java owned by user) is true")
    }

    @Test
    fun `older installed Java triggers Java update when bundled`() {
        val plan = UpdateService.plan(
            remote = remote("1.4.55-dev", java = "1.8.0_999"),
            installed = InstalledVersions(xmageVersion = "1.4.55-dev", javaVersion = "1.8.0_201"),
            useSystemJava = false,
        )
        assertTrue(plan.needsJava)
    }
}
