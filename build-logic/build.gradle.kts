plugins {
    `kotlin-dsl`
}

fun Provider<PluginDependency>.markerCoordinates() =
    map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

dependencies {
    implementation(libs.plugins.ktlint.markerCoordinates())
    implementation(libs.plugins.detekt.markerCoordinates())

    implementation(libs.plugins.kotlin.jvm.markerCoordinates())
}
