package ie.stu.invoker.update

import ie.stu.invoker.config.RemoteConfig
import ie.stu.invoker.settings.InstalledVersions
import org.apache.maven.artifact.versioning.DefaultArtifactVersion

data class UpdatePlan(
    val needsJava: Boolean,
    val needsXMage: Boolean,
    val downgradeXMage: Boolean,
) {
    val anything: Boolean = needsJava || needsXMage
}

object UpdateService {
    fun plan(remote: RemoteConfig, installed: InstalledVersions, useSystemJava: Boolean = false): UpdatePlan {
        val needsJava = !useSystemJava && compareVersions(installed.javaVersion, remote.java.version) < 0
        val cmp = compareVersions(installed.xmageVersion, remote.xmage.version)
        return UpdatePlan(
            needsJava = needsJava,
            needsXMage = cmp < 0,
            downgradeXMage = cmp > 0,
        )
    }

    /** Returns negative if installed < remote, 0 if equal, positive if installed > remote. Null installed counts as -1. */
    private fun compareVersions(installed: String?, remote: String): Int {
        if (installed.isNullOrBlank()) return -1
        return runCatching {
            DefaultArtifactVersion(installed).compareTo(DefaultArtifactVersion(remote))
        }.getOrElse { installed.compareTo(remote) }
    }
}
