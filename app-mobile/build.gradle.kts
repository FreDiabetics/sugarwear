plugins {
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.application")
}

val generatedAnalogPreviewRes = layout.buildDirectory.dir("generated/sugarliciousAnalogPreviewRes")
val generatedOrbitPreviewRes = layout.buildDirectory.dir("generated/orbitPreviewRes")
val generatedNativeSlotRes = layout.buildDirectory.dir("generated/nativeWatchSlotRes")
val nativeSlotSources =
    mapOf(
        "apex" to rootProject.file("watchfaces/sugarlicious-analog/src/main/res/raw/watchface.xml"),
        "orbit" to rootProject.file("watchfaces/sugarlicious-orbit/src/main/res/raw/watchface.xml"),
    )
val syncNativeWatchSlots =
    tasks.register("syncNativeWatchSlots") {
        val sourceFiles = nativeSlotSources
        val targetDirectory = generatedNativeSlotRes
        inputs.files(sourceFiles.values)
        inputs.files(sourceFiles.values.map { it.parentFile.parentFile.resolve("values/strings.xml") })
        outputs.dir(targetDirectory)
        doLast {
            val factory =
                javax.xml.parsers.DocumentBuilderFactory
                    .newInstance()
            sourceFiles.forEach { (name, source) ->
                val labelsDocument = factory.newDocumentBuilder().parse(source.parentFile.parentFile.resolve("values/strings.xml"))
                val labels = labelsDocument.getElementsByTagName("string")
                val displayNames =
                    (0 until labels.length).associate { index ->
                        val element = labels.item(index) as org.w3c.dom.Element
                        element.getAttribute("name") to element.textContent
                    }
                val input = factory.newDocumentBuilder().parse(source)
                val output = factory.newDocumentBuilder().newDocument()
                val root = output.createElement("WatchFaceSlots")
                output.appendChild(root)
                val colorConfigurations = input.getElementsByTagName("ColorConfiguration")
                (0 until colorConfigurations.length).forEach { colorIndex ->
                    root.appendChild(output.importNode(colorConfigurations.item(colorIndex), true))
                }
                val scene = input.getElementsByTagName("Scene").item(0) as org.w3c.dom.Element
                val slots = scene.getElementsByTagName("ComplicationSlot")
                (0 until slots.length).forEach { index ->
                    val slot = slots.item(index) as org.w3c.dom.Element
                    if (slot.getAttribute("isCustomizable") == "TRUE") {
                        val copy = output.importNode(slot, false) as org.w3c.dom.Element
                        copy.setAttribute("displayName", displayNames[slot.getAttribute("displayName")] ?: slot.getAttribute("displayName"))
                        (0 until slot.childNodes.length).forEach { childIndex ->
                            val child = slot.childNodes.item(childIndex)
                            if (child.nodeName.startsWith("Bounding") || child.nodeName == "DefaultProviderPolicy" || child.nodeName == "Complication") copy.appendChild(output.importNode(child, true))
                        }
                        root.appendChild(copy)
                    }
                }
                val target = targetDirectory.get().file("raw/native_${name}_slots.xml").asFile
                target.parentFile.mkdirs()
                javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
                    javax.xml.transform.dom
                        .DOMSource(output),
                    javax.xml.transform.stream
                        .StreamResult(target),
                )
            }
        }
    }
val syncOrbitPreviewAsset =
    tasks.register<Sync>("syncOrbitPreviewAsset") {
        from(rootProject.file("watchfaces/sugarlicious-orbit/src/main/res/drawable-nodpi/preview.webp")) {
            rename { "sugarlicious_orbit_preview.webp" }
        }
        into(generatedOrbitPreviewRes.map { it.dir("drawable-nodpi") })
    }
val syncSugarliciousAnalogPreviewAssets =
    tasks.register<Sync>("syncSugarliciousAnalogPreviewAssets") {
        from(rootProject.file("watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi/apex_template_preview.webp"))
        from(rootProject.file("watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi")) {
            include("hour_hand.png", "minute_hand.png", "second_hand.png")
            rename { "apex_preview_$it" }
        }
        from(rootProject.file("watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi")) {
            include("hour_hand_transparent.png", "minute_hand_transparent.png", "second_hand_transparent.png")
            rename { "orbit_preview_${it.replace("_transparent", "")}" }
        }
        into(generatedAnalogPreviewRes)
        eachFile { path = "drawable-nodpi/$name" }
        includeEmptyDirs = false
    }

android {
    buildFeatures { compose = true }

    namespace = "app.sugarlicious.mobile"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.sugarlicious"
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = rootProject.extra["sugarliciousSuiteVersionCode"] as Int
        versionName = rootProject.extra["sugarliciousSuiteVersionName"] as String
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets["main"].res.directories.add(generatedAnalogPreviewRes.get().asFile.path)
    sourceSets["main"].res.directories.add(generatedOrbitPreviewRes.get().asFile.path)
    sourceSets["main"].res.directories.add(generatedNativeSlotRes.get().asFile.path)
}

tasks.matching { task -> task.name == "preBuild" }.configureEach {
    dependsOn(syncSugarliciousAnalogPreviewAssets)
    dependsOn(syncOrbitPreviewAsset)
    dependsOn(syncNativeWatchSlots)
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.core:core:1.19.1")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation(project(":data-source-therapy"))
    implementation(project(":data-source-manufacturer"))
    implementation(project(":data-source-external"))
    implementation(project(":wear-protocol"))
    implementation(project(":wear-storage"))
    implementation(project(":ui-shared"))
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.17")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
