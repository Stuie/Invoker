package ie.stu.invoker.platform

import java.util.Locale

actual fun detectPlatform(): PlatformInfo = PlatformInfo(detectOs(), detectArch())

private fun detectOs(): Os {
    val name = System.getProperty("os.name").lowercase(Locale.ROOT)
    return when {
        "win" in name -> Os.Windows
        "mac" in name || "darwin" in name -> Os.Mac
        else -> Os.Linux
    }
}

private fun detectArch(): Arch {
    val osArch = System.getProperty("os.arch").lowercase(Locale.ROOT)
    if ("aarch64" in osArch || "arm64" in osArch) return Arch.Arm64

    // Windows: env vars are more reliable than os.arch (which reflects the JVM, not the OS)
    if ("win" in System.getProperty("os.name").lowercase(Locale.ROOT)) {
        val wow64 = System.getenv("PROCESSOR_ARCHITEW6432")
        val proc = System.getenv("PROCESSOR_ARCHITECTURE")
        val arch = (wow64 ?: proc ?: "").lowercase(Locale.ROOT)
        return when {
            "amd64" in arch || "x86_64" in arch || "x64" in arch -> Arch.X64
            "arm64" in arch || "aarch64" in arch -> Arch.Arm64
            else -> Arch.X86
        }
    }

    return when {
        "amd64" in osArch || "x86_64" in osArch || "x64" in osArch -> Arch.X64
        else -> Arch.X86
    }
}
