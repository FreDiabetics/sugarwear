plugins { id("com.android.application") }
android {
    enableKotlin = false
    namespace = "app.aapswear.watchface.sugarlicious.directtowatch"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.sugarwear.watchfacepush.vigil"
        minSdk = 33
        targetSdk = 37
        versionCode = rootProject.extra["sugarwearVersionCode"] as Int
        versionName = rootProject.extra["sugarwearVersionName"] as String
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures { buildConfig = false }
    packaging { resources.excludes += setOf("kotlin/**", "META-INF/*.version", "META-INF/*.kotlin_module") }
    lint { checkReleaseBuilds = false }
}
configurations.configureEach {
    if (name.endsWith("RuntimeClasspath") ||
        name.endsWith("CompileClasspath")
    ) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
}
