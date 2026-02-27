import java.time.Instant

plugins {
    kotlin("multiplatform") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
}

group = "com.monta.gradle.changelog"
version = "1.8.0"

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

defaultTasks("hostBinaries")

// Task to generate version information
val generateVersionInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/kotlin")
    val versionFile = outputDir.get().file("com/monta/changelog/Version.kt").asFile

    outputs.dir(outputDir)

    doLast {
        versionFile.parentFile.mkdirs()

        // Get version from latest git tag
        val version = try {
            val proc = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            proc.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            println("Warning: Could not determine version from git: ${e.message}")
            "dev"
        }

        // Get git commit SHA
        val gitSha = try {
            val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start()
            proc.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            println("Warning: Could not determine git SHA: ${e.message}")
            "unknown"
        }

        // Get build timestamp
        val buildTime = Instant.now().toString()

        versionFile.writeText(
            """
            package com.monta.changelog

            object Version {
                const val VERSION = "$version"
                const val GIT_SHA = "$gitSha"
                const val BUILD_TIME = "$buildTime"

                fun format(): String = "changelog-cli ${'$'}VERSION (commit: ${'$'}GIT_SHA, built: ${'$'}BUILD_TIME)"
            }
            """.trimIndent()
        )
    }
}

kotlin {

    val hostOs = System.getProperty("os.name")

    // JVM target for testing and coverage (doesn't produce executable, just runs tests)
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // Cross Compilation

    val hostTarget = when {
        hostOs == "Mac OS X" -> macosArm64("host")
        hostOs == "Linux" -> linuxX64("host")
        hostOs.startsWith("Windows") -> mingwX64("host")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    hostTarget.apply {
        binaries {
            executable {
                entryPoint = "com.monta.changelog.main"
            }
        }
    }

    // Additional Linux ARM64 target for cross-compilation
    if (hostOs == "Linux") {
        linuxArm64("linuxArm64") {
            binaries {
                executable {
                    entryPoint = "com.monta.changelog.main"
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin"))
            dependencies {
                // CLI
                implementation("com.github.ajalt.clikt:clikt:5.1.0")
                // Date Time Support
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                // Atomic
                implementation("org.jetbrains.kotlinx:atomicfu:0.29.0")
                // Http Client (core only - engines are platform-specific)
                implementation("io.ktor:ktor-client-core:3.3.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.3.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.3")
                // Semver parser
                implementation("io.github.z4kn4fein:semver:3.0.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation("io.kotest:kotest-framework-engine:6.0.7")
                implementation("io.kotest:kotest-assertions-core:6.0.7")
            }
        }

        // JVM-specific test dependencies
        val jvmTest by getting {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:6.0.7")
            }
        }

        // JVM-specific dependencies
        val jvmMain by getting {
            dependencies {
                // Use OkHttp engine for JVM
                implementation("io.ktor:ktor-client-okhttp:3.3.3")
            }
        }

        // Native-specific dependencies
        val nativeMain by creating {
            dependsOn(commonMain)
            dependencies {
                // Use Curl engine for Native
                implementation("io.ktor:ktor-client-curl:3.3.3")
            }
        }

        // Configure native targets to use nativeMain
        val hostMain by getting {
            dependsOn(nativeMain)
        }

        // Configure linuxArm64 if it exists
        if (hostOs == "Linux") {
            val linuxArm64Main by getting {
                dependsOn(nativeMain)
            }
        }
    }
}

kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
    binaries.all {
        freeCompilerArgs = freeCompilerArgs + "-Xdisable-phases=EscapeAnalysis"
    }
}

// Make Kotlin compilation depend on version generation
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateVersionInfo)
}

// Configure ktlint to exclude generated files and depend on version generation
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    dependsOn(generateVersionInfo)
    exclude {
        it.file.path.contains("generated")
    }
}

// Copy binary to root for easier access
tasks.register<Copy>("copyBinary") {
    dependsOn("linkReleaseExecutableHost")
    from(layout.buildDirectory.file("bin/host/releaseExecutable/changelog-cli.kexe"))
    into(layout.projectDirectory)
    rename { "changelog-cli" }
}

// Make hostBinaries also copy the binary
tasks.named("hostBinaries") {
    finalizedBy("copyBinary")
}

// Make build task also copy the binary to root
tasks.named("build") {
    finalizedBy("copyBinary")
}

// Kover configuration for code coverage
// Note: Kover has limited support for Kotlin/Native targets currently.
// Coverage reports will be generated but may show "No sources" until
// JVM test targets are added or Kover improves Native support.
kover {
    reports {
        filters {
            excludes {
                // Exclude generated code
                classes("com.monta.changelog.Version*")
                packages("*.generated.*")
            }
        }

        total {
            html {
                onCheck = false // Don't run on check due to Native limitations
            }

            xml {
                onCheck = false // Don't run on check due to Native limitations
            }

            verify {
                onCheck = false // Disabled until Kover Native support improves
                rule {
                    minBound(50) // Target: minimum 50% coverage
                }
            }
        }
    }
}

// Add tasks to easily generate coverage reports manually
tasks.register("coverage") {
    group = "verification"
    description = "Generate coverage reports (HTML and XML)"
    dependsOn("allTests", "koverHtmlReport", "koverXmlReport")
    doLast {
        println("Coverage reports generated:")
        println("  HTML: file://${layout.buildDirectory.get()}/reports/kover/html/index.html")
        println("  XML:  ${layout.buildDirectory.get()}/reports/kover/report.xml")
    }
}

val hostOs = System.getProperty("os.name")
val isLinux = hostOs == "Linux"

// Task to build both x64 and ARM64 binaries for Linux (only on Linux hosts)
if (isLinux) {
    tasks.register("buildAllLinuxBinaries") {
        group = "build"
        description = "Build binaries for both Linux x64 and ARM64"
        dependsOn("linkReleaseExecutableHost", "linkReleaseExecutableLinuxArm64")
    }

    // Task to copy ARM64 binary with architecture suffix
    tasks.register<Copy>("copyArm64Binary") {
        dependsOn("linkReleaseExecutableLinuxArm64")
        from(layout.buildDirectory.file("bin/linuxArm64/releaseExecutable/changelog-cli.kexe"))
        into(layout.buildDirectory.dir("release"))
        rename { "changelog-cli-arm64" }
    }

    // Task to copy x64 binary with architecture suffix
    tasks.register<Copy>("copyX64Binary") {
        dependsOn("linkReleaseExecutableHost")
        from(layout.buildDirectory.file("bin/host/releaseExecutable/changelog-cli.kexe"))
        into(layout.buildDirectory.dir("release"))
        rename { "changelog-cli-x64" }
    }

    // Task to build and organize all release binaries
    tasks.register("prepareReleaseBinaries") {
        group = "build"
        description = "Build and organize all release binaries for distribution"
        dependsOn("buildAllLinuxBinaries", "copyX64Binary", "copyArm64Binary")
    }
}
