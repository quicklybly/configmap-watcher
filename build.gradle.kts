plugins {
    kotlin("jvm") version "2.4.0"
    `maven-publish`
}

group = "com.quicklybly"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2025.1.2"))
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("org.slf4j:slf4j-api")

    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.cloud:spring-cloud-context")
    compileOnly("jakarta.annotation:jakarta.annotation-api")

    testImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.springframework.cloud:spring-cloud-context")
    testImplementation("jakarta.annotation:jakarta.annotation-api")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.springframework.boot:spring-boot-test")
    testRuntimeOnly("ch.qos.logback:logback-classic")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
