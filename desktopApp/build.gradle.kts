import icons.IconGenerator
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

// Put icon.png on the runtime classpath so the Compose Window (and any other in-app surface)
// can load it via `painterResource("icon.png")`. We deliberately only include the PNG — the
// .ico and .icns containers are jpackage inputs, not runtime assets, and the .svg source has
// no role at runtime either.
sourceSets["main"].resources.srcDir("icons")
sourceSets["main"].resources.include("icon.png")

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

/**
 * Generate icon.png / icon.ico / icon.icns from a single high-resolution source under
 * `desktopApp/icons/`. Not part of the regular build — outputs are committed to source
 * control. Re-run only when the artwork changes:
 *
 *     ./gradlew :desktopApp:generateIcons
 *     ./gradlew :desktopApp:generateIcons -PiconSource=icons/something-else.png
 */
tasks.register("generateIcons") {
    group = "build"
    description = "Generate platform icon files from icons/rocket.png (or -PiconSource=…)."

    // Resolve paths at configuration time so the action can run under the configuration cache.
    val sourceName = (project.findProperty("iconSource") as String?) ?: "icons/rocket.png"
    val source = project.file(sourceName)
    val outDir = project.file("icons")
    val outputs = listOf(File(outDir, "icon.png"), File(outDir, "icon.ico"), File(outDir, "icon.icns"))
    inputs.file(source)
    this.outputs.files(outputs)

    doLast {
        IconGenerator.generate(source, outDir)
        logger.lifecycle("Generated icon.png, icon.ico, icon.icns under $outDir from $source")
    }
}

compose.desktop {
    application {
        mainClass = "ie.stu.invoker.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Invoker"
            packageVersion = project.version.toString().substringBefore("-") // jpackage requires X.Y.Z; strip any -SNAPSHOT/-rc suffix
            description = "Invoker — XMage Launcher"
            vendor = "ie.stu"

            // Icon wiring. Icons are committed under desktopApp/icons/ (see icons/README.md).
            // We only set iconFile when the artwork exists so builds don't fail before the
            // files land — they're treated as a release-time requirement, not a build dep.
            val iconIco = project.file("icons/icon.ico")
            val iconIcns = project.file("icons/icon.icns")
            val iconPng = project.file("icons/icon.png")

            windows {
                if (iconIco.exists()) iconFile.set(iconIco)
                menuGroup = "Invoker"
                perUserInstall = true
                shortcut = true
            }
            linux {
                if (iconPng.exists()) iconFile.set(iconPng)
                packageName = "invoker"
            }
            macOS {
                if (iconIcns.exists()) iconFile.set(iconIcns)
                bundleID = "ie.stu.invoker"
            }
        }
    }
}
