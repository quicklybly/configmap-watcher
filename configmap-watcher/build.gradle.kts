plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.cloud.bom))
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)

    // Consumers bring their own Spring, so these stay off the published POM.
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.cloud.context)
    compileOnly(libs.jakarta.annotation.api)

    testImplementation(libs.spring.boot.autoconfigure)
    testImplementation(libs.spring.cloud.context)
    testImplementation(libs.jakarta.annotation.api)

    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.awaitility.kotlin)
    testImplementation(libs.assertj.core)
    testImplementation(libs.spring.boot.test)
    testRuntimeOnly(libs.logback.classic)
}

kotlin {
    jvmToolchain(21)
}

detekt {
    // Shared with test-application; there is no subprojects {} block, so each module points at it.
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

java {
    withSourcesJar()
}

val javadocJar =
    tasks.register<Jar>("javadocJar") {
        description = "Packages the Dokka HTML documentation as the -javadoc artifact."
        archiveClassifier = "javadoc"
        from(tasks.named("dokkaGeneratePublicationHtml"))
    }

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(javadocJar)

            pom {
                name = "configmap-watcher"
                description = "Spring Boot auto configuration that refreshes the context when a " +
                    "mounted Kubernetes ConfigMap changes."
                url = "https://github.com/quicklybly/configmap-watcher"

                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/license/mit"
                    }
                }
                developers {
                    developer {
                        id = "quicklybly"
                        name = "quicklybly"
                    }
                }
                scm {
                    url = "https://github.com/quicklybly/configmap-watcher"
                    connection = "scm:git:https://github.com/quicklybly/configmap-watcher.git"
                    developerConnection = "scm:git:git@github.com:quicklybly/configmap-watcher.git"
                }
            }
        }
    }
}
