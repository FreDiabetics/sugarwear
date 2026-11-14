plugins { id("com.android.library") }
android {
    namespace = "app.sugarlicious.datasource.manufacturer"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    api(project(":data-source-api"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
