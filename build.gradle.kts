plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

// Single source of truth for the launcher version: gradle.properties (or -Pversion=… in CI).
// Both desktopApp's jpackage packageVersion and the generated BuildInfo.LAUNCHER_VERSION read this.
allprojects {
    version = providers.gradleProperty("version").getOrElse("1.0.0-SNAPSHOT")
}
