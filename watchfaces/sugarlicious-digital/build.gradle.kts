plugins { id("com.android.application") }
android {
    enableKotlin = false
    namespace = "app.sugarlicious.watchface.sugarlicious.digital"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.sugarlicious.watchfacepush.digital"
        minSdk = 33
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
