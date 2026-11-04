plugins {
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
    id("com.google.devtools.ksp") version "2.3.13" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false

    id("com.android.application") version "9.4.1" apply false
    id("com.android.library") version "9.4.1" apply false
    kotlin("android") version "2.4.10" apply false
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    id("dev.detekt") version "2.0.0-alpha.6" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
}

val sharedRoot = project(":core-model").projectDir.parentFile
extra["sharedSourceRoot"] = sharedRoot
extra["sugarwearVersionCode"] = 71
extra["sugarwearVersionName"] = "0.7.1"
apply(from = sharedRoot.resolve("gradle/shared-conventions.gradle"))
apply(from = sharedRoot.resolve("gradle/coverage-conventions.gradle.kts"))

extra["productInstallTaskName"] = "installSugarWearDebug"
extra["productPhoneApks"] = emptyList<String>()
extra["productWatchApks"] = listOf(
    "app-sugarwear/build/outputs/apk/debug/app-sugarwear-debug.apk",
    "watchfaces/vigil/build/outputs/apk/debug/vigil-debug.apk",
)
apply(from = sharedRoot.resolve("gradle/product-installer.gradle"))
tasks.named("installSugarWearDebug") {
    description = "Builds and installs SugarWear and Vigil on exactly one connected Wear OS watch."
    dependsOn(":app-sugarwear:assembleDebug", ":watchfaces:vigil:assembleDebug")
}
