plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    // Pin to Eclipse Temurin via Foojay (configured in settings.gradle.kts). Non-megacorp,
    // TCK-certified, Eclipse Foundation backed. The same toolchain is reused by jpackage
    // to produce the bundled launcher runtime.
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }

    sourceSets {
        commonMain {
            // Add the generated BuildInfo.kt to commonMain so both UI surfaces and unit tests
            // can read the launcher version without a second source of truth.
            kotlin.srcDir(layout.buildDirectory.dir("generated/source/buildinfo/commonMain/kotlin"))
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.materialSymbols.outlined)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.commons.compress)
            implementation(libs.maven.artifact)
            implementation(libs.slf4j.simple)
        }
    }
}

val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/source/buildinfo/commonMain/kotlin")
    val versionProvider = providers.provider { project.version.toString() }
    outputs.dir(outputDir)
    inputs.property("version", versionProvider)
    doLast {
        val file = outputDir.get().asFile.resolve("ie/stu/invoker/BuildInfo.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            // Generated. Do not edit. See shared/build.gradle.kts (generateBuildInfo task).
            package ie.stu.invoker

            object BuildInfo {
                const val LAUNCHER_VERSION = "${versionProvider.get()}"
            }

            """.trimIndent()
        )
    }
}

// Wire the generator before any Kotlin compilation task.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateBuildInfo)
}
