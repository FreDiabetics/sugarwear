plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("net.java.dev.jna:jna:5.18.1")
    testImplementation(kotlin("test"))
}

application { mainClass.set("app.sugarlicious.tools.maintenance.MaintenanceCliKt") }

if (rootProject.file("app-sugarwear/build.gradle.kts").isFile) {
    kotlin.sourceSets.named("test") { kotlin.srcDir("src/standaloneTest/kotlin") }
}

// Windows resolves Cairo's dependent DLLs through PATH, even for an absolute library path.
val cairoLibrary = providers.environmentVariable("SUGARLICIOUS_CAIRO_LIBRARY")
val nativePath =
    providers.environmentVariable("PATH").zip(cairoLibrary) { path, library ->
        file(library).parent + File.pathSeparator + path
    }

tasks.withType<JavaExec>().configureEach {
    nativePath.orNull?.let { environment("PATH", it) }
    workingDir(rootProject.projectDir)
    systemProperty("maintenance.repositoryRoot", rootProject.projectDir.absolutePath)
    systemProperty("maintenance.sharedRoot", projectDir.parentFile.parentFile.absolutePath)
}
val maintenanceArgsFile = providers.gradleProperty("maintenanceArgsFile")
tasks.named<JavaExec>("run") {
    maintenanceArgsFile.orNull?.let { args("--arguments-file", it) }
}
val ownsSugarliciousProduct = rootProject.findProject(":app-mobile") != null
tasks.test {
    nativePath.orNull?.let { environment("PATH", it) }
    useJUnitPlatform {
        // Live Mobile, Orbit and ApeX assets are only present in the Sugarlicious repository.
        if (!ownsSugarliciousProduct) excludeTags("sugarlicious-product")
    }
    systemProperty("maintenance.repositoryRoot", rootProject.projectDir.absolutePath)
    systemProperty("maintenance.sharedRoot", projectDir.parentFile.parentFile.absolutePath)
}
