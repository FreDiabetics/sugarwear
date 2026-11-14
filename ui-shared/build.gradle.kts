plugins {
    id("com.android.library")
}

android {
    namespace = "app.sugarlicious.uishared"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.11.0")
    api(project(":core-model"))
    implementation("androidx.core:core:1.19.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.17")
}
