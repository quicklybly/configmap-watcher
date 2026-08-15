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
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.cloud.starter)
    implementation(libs.kotlin.logging)
    implementation(kotlin("reflect"))

    testImplementation(kotlin("test"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.awaitility.kotlin)
    testImplementation(libs.assertj.core)
}

kotlin {
    jvmToolchain(21)
}

allOpen {
    annotation("org.springframework.cloud.context.config.annotation.RefreshScope")
}

tasks.test {
    useJUnitPlatform()
}

tasks.bootJar {
    archiveFileName.set("test-application.jar")
}
