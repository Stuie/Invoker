package ie.stu.invoker.platform

enum class Os(val configSuffix: String) {
    Windows("windows"),
    Mac("macosx"),
    Linux("linux"),
}

enum class Arch(val configSuffix: String) {
    X86("x86"),
    X64("x64"),
    Arm64("aarch64"),
}

data class PlatformInfo(val os: Os, val arch: Arch) {
    val javaArchiveExtension: String = if (os == Os.Windows) "zip" else "tar.gz"
    val javaUrlSuffix: String = "${os.configSuffix}-${arch.configSuffix}.$javaArchiveExtension"
}

expect fun detectPlatform(): PlatformInfo
