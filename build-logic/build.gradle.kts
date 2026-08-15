plugins {
    `kotlin-dsl`
}

// A precompiled script plugin applies plugins by id, not by catalog alias, and can only apply
// what is on this build's classpath. Each one is therefore added below as its marker artifact -
// the coordinates Gradle resolves a plugin id to - so versions still come from the catalog.
fun Provider<PluginDependency>.markerCoordinates() =
    map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

dependencies {
    implementation(libs.plugins.ktlint.markerCoordinates())
    implementation(libs.plugins.detekt.markerCoordinates())

    // Not applied by the convention plugin, but ktlint 13 touches the Kotlin Gradle plugin's
    // KotlinProjectExtension while it configures itself, and without this on the classpath
    // applying lint-conventions dies with NoClassDefFoundError. compileOnly is not enough -
    // it is needed at execution time. The modules still apply the Kotlin plugin themselves,
    // at this same catalog version.
    implementation(libs.plugins.kotlin.jvm.markerCoordinates())
}
