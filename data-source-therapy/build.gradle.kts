plugins { id("com.android.library") }
android {
    namespace = "app.sugarlicious.datasource.therapybridge"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    testOptions {
        unitTests.isIncludeAndroidResources =
            true
    }
}
dependencies {
    api(project(":data-source-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.robolectric:robolectric:4.17")
}
tasks.withType<Test>().configureEach { useJUnitPlatform() }
