plugins {
    kotlin("jvm")
    application
}

dependencies { testImplementation(kotlin("test")) }

application { mainClass.set("app.sugarlicious.tools.screenshot.ComparatorCliKt") }

tasks.test { useJUnitPlatform() }
