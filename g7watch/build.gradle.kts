plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.aapswear.g7watch"
    compileSdk = 37
    defaultConfig {
        applicationId = if (providers.gradleProperty("legacyProductMigration").orNull == "true") "app.aapswear.g7watch" else "app.sugarwear"
        minSdk = 33
        targetSdk = 37
        versionCode = rootProject.extra["sugarwearVersionCode"] as Int
        versionName = rootProject.extra["sugarwearVersionName"] as String
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

val vigilPreviewDebugAssets = layout.buildDirectory.dir("generated/vigilPreviewDebug/assets")
val syncVigilPreviewDebugAssets =
    tasks.register<Sync>("syncVigilPreviewDebugAssets") {
        into(vigilPreviewDebugAssets)
        from(rootProject.file("watchfaces/vigil/src/main/res/raw/watchface.xml")) {
            // Reference application data, not a res/raw Watch Face Format application.
            rename { "vigil_preview_definition.xml" }
        }
        from(rootProject.file("watchfaces/vigil/src/main/res/drawable-nodpi")) {
            include("vigil_*markers_close.png", "vigil_*markers_wide.png", "second_hand_transparent.png")
        }
    }
android.sourceSets
    .getByName("debug")
    .assets.directories
    .add(vigilPreviewDebugAssets.get().asFile.path)
tasks.configureEach {
    if (name == "preDebugBuild") dependsOn(syncVigilPreviewDebugAssets)
}

dependencies {
    implementation(project(":dexcom-g7"))
    implementation(project(":wear-storage"))
    implementation(project(":wear-protocol"))
    implementation(project(":ui-shared"))
    implementation(project(":complications"))
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("com.google.guava:guava:33.7.2-android")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.17")
}
