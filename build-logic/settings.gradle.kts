// An included build, not buildSrc: buildSrc puts its whole runtime classpath on every build
// script in the main build, which collides with the versioned plugin aliases the modules
// already declare. See the note in build.gradle.kts about the Kotlin Gradle plugin.
rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
    }
    // A separate build does not inherit the main build's settings, so the catalog has to be
    // imported explicitly to keep every version in gradle/libs.versions.toml.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
