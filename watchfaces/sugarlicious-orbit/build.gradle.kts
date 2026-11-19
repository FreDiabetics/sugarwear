plugins { id("com.android.application") }

val generatedHands = layout.buildDirectory.dir("generated/orbitHands")
val syncCanonicalHands =
    tasks.register<Sync>("syncCanonicalHands") {
        from(rootProject.file("watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi")) {
            include("hour_hand*.png", "minute_hand*.png", "second_hand*.png", "sugarlicious_analog_template.png", "inline_image_empty.png")
        }
        into(generatedHands.map { it.dir("drawable-nodpi") })
    }

android {
    enableKotlin = false
    namespace = "app.sugarlicious.watchface.orbit"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.sugarlicious.watchfacepush.orbit"
        minSdk = 36
        targetSdk = 37
        versionCode = rootProject.extra["sugarliciousSuiteVersionCode"] as Int
        versionName = rootProject.extra["sugarliciousSuiteVersionName"] as String
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    sourceSets["main"].res.directories.add(generatedHands.get().asFile.path)
    buildFeatures { buildConfig = false }
    packaging { resources.excludes += setOf("kotlin/**", "META-INF/*.version", "META-INF/*.kotlin_module") }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(syncCanonicalHands) }
configurations.configureEach {
    if (name.endsWith("RuntimeClasspath") || name.endsWith("CompileClasspath")) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
}
