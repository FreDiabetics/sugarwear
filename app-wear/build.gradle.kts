import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

plugins {
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

@CacheableTask
abstract class PrepareDefaultWatchFaceTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        @get:InputFile
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val watchFaceApk: RegularFileProperty

        @get:Classpath
        abstract val validatorJars: ConfigurableFileCollection

        @get:OutputFile
        abstract val outputApk: RegularFileProperty

        @get:Optional
        @get:OutputFile
        abstract val selectableApk: RegularFileProperty

        @get:Optional
        @get:OutputFile
        abstract val selectableToken: RegularFileProperty

        @get:OutputFile
        abstract val outputTokenResource: RegularFileProperty

        @get:Input
        abstract val tokenResourceName: Property<String>

        @TaskAction
        fun prepare() {
            val validator =
                validatorJars.files
                    .filter { file ->
                        file.name.startsWith("validator-push-cli-") && file.extension == "jar"
                    }.maxByOrNull { it.lastModified() }
                    ?: error("Watch Face Push validator CLI is missing")
            val validatorOutput = ByteArrayOutputStream()
            val result =
                execOperations.exec {
                    commandLine(
                        "java",
                        "-jar",
                        validator.absolutePath,
                        "--apk_path=${watchFaceApk.get().asFile.absolutePath}",
                        "--package_name=app.sugarlicious",
                    )
                    standardOutput = validatorOutput
                    errorOutput = validatorOutput
                    isIgnoreExitValue = true
                }
            val output = validatorOutput.toString(Charsets.UTF_8)
            check(result.exitValue == 0) { output }
            val token =
                Regex("(?im)(?:Validation token:|generated token:)\\s*([^\\r\\n]+)")
                    .find(output)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: error("Default watchface validation token is missing:\n$output")

            val asset = outputApk.get().asFile
            asset.parentFile.mkdirs()
            watchFaceApk.get().asFile.copyTo(asset, overwrite = true)

            if (selectableApk.isPresent) {
                val selectableAsset = selectableApk.get().asFile
                selectableAsset.parentFile.mkdirs()
                watchFaceApk.get().asFile.copyTo(selectableAsset, overwrite = true)
            }

            if (selectableToken.isPresent) {
                val selectableTokenAsset = selectableToken.get().asFile
                selectableTokenAsset.parentFile.mkdirs()
                selectableTokenAsset.writeText(token)
            }

            val resource = outputTokenResource.get().asFile
            resource.parentFile.mkdirs()
            val resourceName = tokenResourceName.get()
            resource.writeText(
                if (resourceName.isBlank()) {
                    token
                } else {
                    "<resources>\n" +
                        "    <string name=\"$resourceName\" translatable=\"false\">$token</string>\n" +
                        "</resources>\n"
                },
            )
        }
    }

val generatedWatchFaceAssets = layout.buildDirectory.dir("generated/watchfacePushAssets")
val generatedWatchFaceResources = layout.buildDirectory.dir("generated/watchfacePushRes")
val defaultWatchFaceApk =
    rootProject.layout.projectDirectory.file(
        "watchfaces/sugarlicious-digital/build/outputs/apk/release/sugarlicious-digital-release.apk",
    )
val analogWatchFaceApk =
    rootProject.layout.projectDirectory.file(
        "watchfaces/sugarlicious-analog/build/outputs/apk/release/sugarlicious-analog-release.apk",
    )
val validatorDirectory = rootProject.layout.buildDirectory.dir("watchface-push/tools")
val orbitWatchFaceApk =
    rootProject.layout.projectDirectory.file(
        "watchfaces/sugarlicious-orbit/build/outputs/apk/release/sugarlicious-orbit-release.apk",
    )

val prepareDefaultWatchFace =
    tasks.register<PrepareDefaultWatchFaceTask>("prepareDefaultWatchFace") {
        dependsOn(
            ":watchfaces:sugarlicious-digital:assembleRelease",
            ":prepareWatchFaceValidatorCli",
        )

        watchFaceApk.set(defaultWatchFaceApk)
        validatorJars.from(
            rootProject.fileTree(validatorDirectory) {
                include("validator-push-cli-*.jar")
            },
        )
        outputApk.set(generatedWatchFaceAssets.map { it.file("default_watchface.apk") })
        selectableApk.set(
            generatedWatchFaceAssets.map { it.file("watchfaces/sugarlicious_digital.apk") },
        )
        selectableToken.set(
            generatedWatchFaceAssets.map { it.file("watchfaces/sugarlicious_digital_token.txt") },
        )
        outputTokenResource.set(
            generatedWatchFaceResources.map {
                it.file("values/default_watchface_token.xml")
            },
        )
        tokenResourceName.set("default_wf_token")
    }

val prepareAnalogWatchFace =
    tasks.register<PrepareDefaultWatchFaceTask>("prepareAnalogWatchFace") {
        dependsOn(
            ":watchfaces:sugarlicious-analog:assembleRelease",
            ":prepareWatchFaceValidatorCli",
        )
        watchFaceApk.set(analogWatchFaceApk)
        validatorJars.from(
            rootProject.fileTree(validatorDirectory) { include("validator-push-cli-*.jar") },
        )
        outputApk.set(generatedWatchFaceAssets.map { it.file("watchfaces/sugarlicious_analog.apk") })
        outputTokenResource.set(
            generatedWatchFaceAssets.map { it.file("watchfaces/sugarlicious_analog_token.txt") },
        )
        tokenResourceName.set("")
    }

val prepareOrbitWatchFace =
    tasks.register<PrepareDefaultWatchFaceTask>("prepareOrbitWatchFace") {
        dependsOn(":watchfaces:sugarlicious-orbit:assembleRelease", ":prepareWatchFaceValidatorCli")
        watchFaceApk.set(orbitWatchFaceApk)
        validatorJars.from(rootProject.fileTree(validatorDirectory) { include("validator-push-cli-*.jar") })
        outputApk.set(generatedWatchFaceAssets.map { it.file("watchfaces/sugarlicious_orbit.apk") })
        outputTokenResource.set(generatedWatchFaceAssets.map { it.file("watchfaces/sugarlicious_orbit_token.txt") })
        tokenResourceName.set("")
    }

tasks.configureEach {
    if (
        name != prepareDefaultWatchFace.name &&
        name != prepareAnalogWatchFace.name &&
        name != prepareOrbitWatchFace.name &&
        (
            name.contains("Assets") ||
                name.contains("Resources") ||
                name.endsWith("SourceSetPaths")
        )
    ) {
        dependsOn(prepareDefaultWatchFace, prepareAnalogWatchFace, prepareOrbitWatchFace)
    }
}

android {
    namespace = "app.sugarlicious.wear"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.sugarlicious"
        minSdk = 33
        targetSdk = 37
        versionCode = rootProject.extra["sugarliciousSuiteVersionCode"] as Int
        versionName = rootProject.extra["sugarliciousSuiteVersionName"] as String
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures { compose = true }
    sourceSets["main"].assets.directories.add(generatedWatchFaceAssets.get().asFile.path)
    // Old build directories may still contain Vigil from earlier coupled builds.
    // Keep those generated files out of Sugarlicious even without a clean build.
    androidResources.ignoreAssetsPattern = "sugarlicious_direct_to_watch.apk:sugarlicious_direct_to_watch_token.txt"
    sourceSets["main"].res.directories.add(generatedWatchFaceResources.get().asFile.path)
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation(project(":wear-protocol"))
    implementation(project(":wear-storage"))
    implementation(project(":complications"))
    implementation(project(":ui-shared"))
    implementation(project(":cgm-protocol"))
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.3.0")
    implementation("androidx.wear.watchfacepush:watchfacepush:1.0.0")
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("androidx.wear.protolayout:protolayout-material3:1.4.2")
    implementation("com.google.guava:guava:33.7.2-android")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.17")
}
