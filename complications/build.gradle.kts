plugins { id("com.android.library") }
android {
    namespace = "app.sugarlicious.complications"
    compileSdk = 37
    defaultConfig { minSdk = 33 }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

val vigilHandResources = layout.buildDirectory.dir("generated/vigilHands/res")
val syncVigilHandResources =
    tasks.register<Sync>("syncVigilHandResources") {
        into(vigilHandResources)
        from(projectDir.parentFile.resolve("watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi")) {
            include("hour_hand_transparent.png", "minute_hand_transparent.png", "second_hand_transparent.png")
            into("drawable-nodpi")
        }
    }
androidComponents {
    onVariants { variant ->
        variant.sources.res?.addStaticSourceDirectory(vigilHandResources.get().asFile.path)
    }
}
tasks.configureEach {
    if (name == "preBuild") dependsOn(syncVigilHandResources)
}
dependencies {
    api(project(":wear-storage"))
    implementation(project(":wear-protocol"))
    implementation(project(":ui-shared"))
    api("androidx.wear.watchface:watchface-complications-data-source-ktx:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.17")
}
