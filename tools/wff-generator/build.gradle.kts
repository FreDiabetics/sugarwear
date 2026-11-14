plugins {
    kotlin("jvm")
    application
}
dependencies {
    implementation(project(":tools:watchface-package-parser"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    testImplementation(kotlin("test"))
}
application { mainClass.set("app.sugarlicious.tools.wff.GeneratorCliKt") }
tasks.test { useJUnitPlatform() }
