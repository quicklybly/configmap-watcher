# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot autoconfiguration library that watches Kubernetes ConfigMap mount paths and calls
`ConfigDataContextRefresher.refresh()` when their contents change, so a pod picks up config changes without a restart.
Two Gradle modules, each with its own `CLAUDE.md` covering its traps, plus a Kubernetes test
environment:

| Directory            | What it is                                                                                          |
|----------------------|-----------------------------------------------------------------------------------------------------|
| `configmap-watcher/` | The published library. No `main`. See `configmap-watcher/CLAUDE.md`.                                |
| `test-application/`  | A Spring Boot app consuming the library, and the end to end test. See `test-application/CLAUDE.md`. |
| `k8s/`               | Not a Gradle module: a kind cluster that proves the flow against a real kubelet. See `k8s/README.md`. |

The three layers test progressively less simulated things: the library tests fake the kubelet's
update shape in a temp directory and assert against a mocked refresher, `test-application`'s test
proves a refresh really reaches a `@RefreshScope` bean but rewrites the file in place, and `k8s/`
removes both fakes.

The root project is named `configmap-watcher-parent` so the library directory can keep the name
`configmap-watcher` and the published artifactId stays `com.quicklybly:configmap-watcher`.

## Commands

```bash
./gradlew build                          # both modules: compile + test + jars
./gradlew :configmap-watcher:test        # library tests only
./gradlew :test-application:test         # end to end test only
./gradlew :test-application:bootRun      # run the app (watcher logs "disabled" without a location)
./gradlew publishToMavenLocal            # jar + -sources + -javadoc + pom into ~/.m2

./gradlew test --tests '*ConfigMapWatcherConfigurationTest'                       # one class
./gradlew test --tests '*FileSystemConfigMapWatcherTest.watches the last of several comma separated paths'   # one method (backtick names work quoted)
./gradlew test -i               # also prints logback output; Gradle swallows stdout otherwise
./gradlew test --rerun-tasks    # force a re-run when the test task is UP-TO-DATE
```

```bash
./gradlew ktlintCheck      # formatting only, driven entirely by .editorconfig
./gradlew ktlintFormat     # autofix everything ktlintCheck reports
./gradlew detekt           # code smells; HTML report under <module>/build/reports/detekt/
./gradlew lint             # both of the above
./gradlew lintFormat       # autocorrect pass: ktlintFormat, then detektFormat
```

Both run as part of `check`, so a plain `./gradlew build` fails on a violation.

`lintFormat` is the only task in the build that rewrites sources, and nothing depends on it - it
runs when you type it and never otherwise, so `check` and CI stay read-only. It fixes what ktlint
can fix, then fails on the first detekt smell that needs a human; that failure list is the to-do.

```bash
k8s/up.sh                  # create the kind cluster, build and load the image, deploy
k8s/set-message.sh hello   # change the watched value and watch the pod pick it up
k8s/redeploy.sh            # rebuild the image and roll the deployment, after an app change
k8s/down.sh                # delete the cluster
```

## Style and linting

- **`.editorconfig` is the single source of truth for formatting**, and ktlint reads it directly.
  It sets `ktlint_code_style = intellij_idea`, *not* ktlint's default `ktlint_official` - the latter
  adds opinions IntelliJ's formatter does not have (it strips the blank line after a class header
  and force-wraps chained calls), which would fight `.idea/codeStyles/Project.xml`, where the
  project pins `KOTLIN_OFFICIAL`. Changing that one line reformats the whole codebase.
- **Linting is configured once, in `build-logic/src/main/kotlin/lint-conventions.gradle.kts`.** Both
  modules apply it as `id("lint-conventions")` and configure nothing themselves. Change a rule, a
  report or the task wiring there, never in a module.
- **detekt config lives in `config/detekt/detekt.yml` and holds overrides only**; the convention
  plugin sets `buildUponDefaultConfig = true`, so unlisted rules keep detekt's defaults.
- **detekt's `formatting` ruleset is deliberately absent.** It is a ktlint wrapper, and ktlint
  already runs as its own plugin; adding `detekt-formatting` would report every violation twice.
- **`detektFormat` sets `autoCorrect = true`, and that corrects nothing today.** No ruleset detekt
  bundles implements autocorrection - it lives entirely in `detekt-formatting`, which is absent for
  the reason above, so every actual fix in `lintFormat` comes from ktlint. The task still earns its
  place: it reports the smells ktlint cannot fix, and the wiring is already right if an
  autocorrecting ruleset is ever added. Do not read `autoCorrect = true` as evidence it rewrites.
- **`detektFormat` is a separate task, not `autoCorrect` on `detekt`.** Setting it on the shared
  task would make every `check` - and so every CI run - rewrite tracked sources.
- **`detektFormat` takes its source from the detekt *extension*, not from the `detekt` task.**
  `tasks.named<Detekt>("detekt").map { it.source }` is the same file set, but Gradle infers a task
  dependency on `detekt` from it, which closes a cycle against the `mustRunAfter` wiring below.
- **The format/check ordering is wired on the task types, not the aggregates.** `ktlintCheck` and
  `ktlintFormat` are lifecycle tasks, and `mustRunAfter` does not propagate to a task's
  dependencies, so ordering the aggregates orders nothing and the real workers still race.
  `KtLintCheckTask` and `KtLintFormatTask` are what carry the constraint.
- **`check` depends on a `lint` aggregate task, and `test` declares `mustRunAfter(lint)`.** Without
  it the linters and `test` are unordered siblings under `check` and lint winning the race is
  incidental. `mustRunAfter` orders without adding a dependency, so `./gradlew test` on its own
  still runs no linters - the ordering only applies when both are already in the task graph.

### Why `build-logic`, not `buildSrc`

`buildSrc` puts its entire runtime classpath on every build script in the main build. The Kotlin
Gradle plugin has to be on that classpath (ktlint 13 touches `KotlinProjectExtension` as it
configures, and fails with `NoClassDefFoundError` without it - `compileOnly` is not enough, it is
needed at execution time). From `buildSrc` that leaks, and the modules' own
`alias(libs.plugins.kotlin.jvm)` then fails with *"already on the classpath with an unknown
version"*. An included build resolved through `pluginManagement { includeBuild(...) }` does not
leak, so both can coexist.

`build-logic` is a separate build and inherits nothing from the root `settings.gradle.kts`, so it
imports `gradle/libs.versions.toml` itself. It applies plugins by id rather than by alias, so each
one is declared in `build-logic/build.gradle.kts` as its marker artifact
(`<id>:<id>.gradle.plugin:<version>`) with the version still read from the catalog.
The Kubernetes environment is driven by scripts, not Gradle - the image builds the application from
source itself, so nothing has to be built on the host first:

## Build conventions

- **All versions live in `gradle/libs.versions.toml`.** Modules reference `libs.*` aliases; the root
  `build.gradle.kts` only declares plugins with `apply false`. There is no `subprojects {}` or
  `allprojects {}` block - each module configures itself, and shared setup is a convention plugin in
  `build-logic/` that a module opts into by id (see "Style and linting").
- **`group` and `version` come from `gradle.properties`**, which Gradle applies to every project, so neither module
  repeats them.
- Both modules import the Spring Boot and Spring Cloud BOMs as `platform(...)` dependencies, so most catalog entries
  carry no version of their own.

## Publishing

`configmap-watcher/build.gradle.kts` publishes four artifacts: the jar, `-sources` (via
`java { withSourcesJar() }`), `-javadoc` (a `Jar` task packaging Dokka's HTML output), and a POM.

- **Dokka runs in V2 mode**, which is the default from 2.1.0 - hence the task name
  `dokkaGeneratePublicationHtml`. Under 2.0.0 the plugin silently falls back to V1, where that task does not exist and
  the build fails.
- **The POM metadata blocks (`name`, `description`, `url`, `licences`, `developers`, `scm`) exist because Maven Central
  rejects bundles without them**, not because anything reads them at build time. `scm` mirrors the `origin` remote.
- **`compileOnly` dependencies are deliberately absent from the POM** - consumers bring their own Spring. Only
  `kotlin-stdlib`, `kotlin-logging` and `slf4j-api` are declared.
- Central would additionally need signed artifacts and portal credentials; neither is configured. There is no `LICENSE`
  file in the repo yet, though the POM declares MIT.
