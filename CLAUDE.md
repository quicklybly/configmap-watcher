# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot autoconfiguration library that watches Kubernetes ConfigMap mount paths and calls
`ConfigDataContextRefresher.refresh()` when their contents change, so a pod picks up config changes without a restart.
Two Gradle modules, each with its own `CLAUDE.md` covering its traps:

| Module               | What it is                                                                                          |
|----------------------|-----------------------------------------------------------------------------------------------------|
| `configmap-watcher/` | The published library. No `main`. See `configmap-watcher/CLAUDE.md`.                                |
| `test-application/`  | A Spring Boot app consuming the library, and the end to end test. See `test-application/CLAUDE.md`. |

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

There is no linter configured.

## Build conventions

- **All versions live in `gradle/libs.versions.toml`.** Modules reference `libs.*` aliases; the root
  `build.gradle.kts` only declares plugins with `apply false`. There is no `subprojects {}` or
  `allprojects {}` block - each module configures itself.
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
