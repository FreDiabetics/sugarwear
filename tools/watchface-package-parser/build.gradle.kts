plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    testImplementation(kotlin("test"))
}
application { mainClass.set("app.sugarlicious.tools.cwf.ParserCliKt") }
tasks.test { useJUnitPlatform() }
