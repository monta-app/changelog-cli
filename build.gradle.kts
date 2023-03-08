plugins {
    kotlin("multiplatform") version "1.8.10"
    kotlin("plugin.serialization") version "1.8.10"
    id("io.kotest.multiplatform") version "5.5.4"
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
}

group = "com.monta.gradle.changelog"
version = "1.4.2"

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

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
                implementation("com.github.ajalt.clikt:clikt:3.5.2")
                // Date Time Support
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
                // Atomic
                implementation("org.jetbrains.kotlinx:atomicfu:0.20.0")
                // Http Client
                val ktorVersion = "2.2.4"
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-curl:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                val kotestVersion = "5.5.4"
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("io.kotest:kotest-framework-engine:$kotestVersion")
                implementation("io.kotest:kotest-assertions-core:$kotestVersion")
            }
        }
    }
}

kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
    binaries.all {
        freeCompilerArgs = freeCompilerArgs + "-Xdisable-phases=EscapeAnalysis"
    }
}
