// A real Spring Boot application that consumes the library, so the auto configuration, the
// AutoConfiguration.imports entry and the actual context refresh are exercised end to end.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    id("lint-conventions")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.cloud.bom))

    implementation(project(":configmap-watcher"))
    implementation(libs.spring.boot.starter)
    // Supplies the ConfigDataContextRefresher the library's @Bean needs.
    implementation(libs.spring.cloud.starter)

    testImplementation(kotlin("test"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.awaitility.kotlin)
    testImplementation(libs.assertj.core)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
