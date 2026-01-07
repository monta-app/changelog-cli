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

defaultTasks("commonBinaries")

kotlin {

    val hostOs = System.getProperty("os.name")

    // Cross Compilation

    val commonTarget = when {
        hostOs == "Mac OS X" -> macosArm64("common")
        hostOs == "Linux" -> linuxX64("common")
        hostOs.startsWith("Windows") -> mingwX64("common")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    commonTarget.apply {
        binaries {
            executable {
                entryPoint = "com.monta.changelog.main"
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
                implementation("org.jetbrains.kotlinx:atomicfu:0.29.0")
                // Http Client
                implementation("io.ktor:ktor-client-core:3.1.3")
                implementation("io.ktor:ktor-client-curl:3.1.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.1.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
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
