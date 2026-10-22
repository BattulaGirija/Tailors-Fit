import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Tailors Fit for Windows: the same pattern engine as the Android app, with a desktop UI.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":pattern-core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
}

val build = (System.getenv("BUILD_NUMBER") ?: "0").toInt().coerceIn(0, 65535)

compose.desktop {
    application {
        mainClass = "com.tailorsfit.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "TailorsFit"
            packageVersion = "1.0.$build"
            description = "Tailors Fit: blouse, kurti and lehenga patterns from measurements"
            vendor = "Tailors Fit"
            windows {
                menuGroup = "Tailors Fit"
                shortcut = true
                menu = true
                perUserInstall = true
                dirChooser = true
                // Keeps the same id so new versions install over old ones.
                upgradeUuid = "6f4d2a3e-8b1c-4e7a-9d5f-2c3b4a5e6f70"
            }
        }
    }
}
