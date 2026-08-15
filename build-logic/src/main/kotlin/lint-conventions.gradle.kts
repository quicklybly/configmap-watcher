import org.gradle.api.tasks.testing.Test

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

val lint = tasks.register("lint") {
    group = "verification"
    description = "Runs ktlint and detekt."
    dependsOn(tasks.named("ktlintCheck"), tasks.named("detekt"))
}

tasks.named("check") {
    dependsOn(lint)
}

tasks.withType<Test>().configureEach {
    mustRunAfter(lint)
}
