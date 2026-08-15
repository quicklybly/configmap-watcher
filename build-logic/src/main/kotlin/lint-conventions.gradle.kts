import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.tasks.testing.Test
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

val detektSource = the<DetektExtension>().source

val lint = tasks.register("lint") {
    group = "verification"
    description = "Runs ktlint and detekt."
    dependsOn(tasks.named("ktlintCheck"), tasks.named("detekt"))
}

val detektFormat = tasks.register<Detekt>("detektFormat") {
    group = "formatting"
    description = "Runs detekt with autocorrect enabled."
    autoCorrect = true
    setSource(detektSource)
    reports {
        html.required = false
        xml.required = false
        txt.required = false
        sarif.required = false
        md.required = false
    }
    mustRunAfter(tasks.withType<KtLintFormatTask>())
}

val lintFormat = tasks.register("lintFormat") {
    group = "formatting"
    description = "Autocorrects what ktlint and detekt can fix."
    dependsOn(tasks.named("ktlintFormat"), detektFormat)
}

tasks.withType<KtLintCheckTask>().configureEach {
    mustRunAfter(tasks.withType<KtLintFormatTask>(), detektFormat)
}

tasks.named<Detekt>("detekt") {
    mustRunAfter(tasks.withType<KtLintFormatTask>(), detektFormat)
}

tasks.named("check") {
    dependsOn(lint)
}

tasks.withType<Test>().configureEach {
    mustRunAfter(lint)
}
