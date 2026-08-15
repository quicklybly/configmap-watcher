import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    id("lint-conventions")
    alias(libs.plugins.maven.publish)
}

repositories {
    mavenCentral()
}

dependencies {
    // The BOMs only exist to version the compileOnly Spring dependencies below, so they must not
    // be `implementation`: that puts them in runtimeElements, and Gradle consumers then inherit
    // Spring Boot and Spring Cloud version constraints from this library - the opposite of
    // "consumers bring their own Spring". Maven consumers never saw this (dependencyManagement is
    // not transitive), which is why it is easy to miss. Declared twice because testImplementation
    // extends implementation, not compileOnly.
    compileOnly(platform(libs.spring.boot.bom))
    compileOnly(platform(libs.spring.cloud.bom))
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.cloud.bom))

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

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = true,
        ),
    )

    publishToMavenCentral()

    // Central rejects unsigned bundles, but signing unconditionally would break every local
    // publishToMavenLocal for anyone without a GPG key configured. The release workflow sets
    // ORG_GRADLE_PROJECT_signingInMemoryKey, so CI always takes this branch; if the secret were
    // ever missing the upload fails loudly at Central rather than publishing something unsigned.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

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
