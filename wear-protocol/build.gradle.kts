plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
dependencies {
    api(project(":core-model"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
