plugins {
    kotlin("multiplatform") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"
    id("io.kotest") version "6.0.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
}

group = "com.monta.gradle.changelog"
version = "1.8.0"

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

defaultTasks("hostBinaries")

kotlin {

    val hostOs = System.getProperty("os.name")

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
            dependencies {
                // CLI
                implementation("com.github.ajalt.clikt:clikt:5.0.3")
                // Date Time Support
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
                // Atomic
                implementation("org.jetbrains.kotlinx:atomicfu:0.27.0")
                // Http Client
                implementation("io.ktor:ktor-client-core:3.3.3")
                implementation("io.ktor:ktor-client-curl:3.3.3")
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
    }
}

kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
    binaries.all {
        freeCompilerArgs = freeCompilerArgs + "-Xdisable-phases=EscapeAnalysis"
    }
}

// KSP configuration for Kotest
dependencies {
    add("kspCommonMainMetadata", "io.kotest:kotest-framework-engine:6.0.7")
}

// Configure ktlint to exclude generated KSP files
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
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
