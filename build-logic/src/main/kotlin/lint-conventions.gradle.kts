import org.gradle.api.tasks.testing.Test

// The single place linting is configured. Both modules apply this as id("lint-conventions"),
// so there is no subprojects {} or allprojects {} block and each module still opts in itself.
plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

// Without this the linters and the tests are unordered siblings under `check`, and which one
// runs first is incidental. shouldRunAfter is a soft constraint: it orders them when both are
// in the task graph, but does not drag the linters in when you run `gradle test` on its own.
tasks.withType<Test>().configureEach {
    shouldRunAfter("ktlintCheck", "detekt")
}
