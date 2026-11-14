plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":core-model"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.bouncycastle:bcprov-jdk15to18:1.81")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
