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
| `.github/workflows/` | `ci.yml` runs `./gradlew build` on every PR; `release.yml` fires on a `v*` tag. See "Publishing".     |

The three layers test progressively less simulated things: the library tests fake the kubelet's
update shape in a temp directory and assert against a mocked refresher, `test-application`'s test
proves a refresh really reaches a `@RefreshScope` bean but rewrites the file in place, and `k8s/`
removes both fakes.

The root project is named `configmap-watcher-parent` so the library directory can keep the name
`configmap-watcher` and the published artifactId stays `io.github.quicklybly:configmap-watcher`.

The `io.github.` prefix is not cosmetic: it is the namespace Maven Central auto-verifies for a
GitHub-linked account, which is what makes publishing possible without owning a domain. The Kotlin
packages match it (`io.github.quicklybly.configmapwatcher.*`) - `com.quicklybly` would claim a
domain nobody owns.

## Commands

```bash
./gradlew build                          # both modules: compile + test + jars
./gradlew :configmap-watcher:test        # library tests only
./gradlew :test-application:test         # end to end test only
./gradlew :test-application:bootRun      # run the app (watcher logs "disabled" without a location)
./gradlew publishToMavenLocal            # jar + -sources + -javadoc + pom into ~/.m2
./gradlew publishToMavenLocal -Pversion=0.1.0-RC1   # exactly what CI publishes, under a real version

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
- **In the library those BOMs are `compileOnly` plus `testImplementation`, never `implementation`.**
  `implementation(platform(...))` puts them in `runtimeElements`, and every Gradle consumer then
  inherits Spring Boot and Spring Cloud version constraints from a library whose whole premise is
  that consumers bring their own Spring. Maven consumers never see it - `dependencyManagement` is
  not transitive - so the POM looks fine and only Gradle resolution is affected. They are declared
  twice because `testImplementation` extends `implementation`, not `compileOnly`.
- **`slf4j-api` therefore pins its version in the catalog**, unlike the other Spring-managed
  entries. It is a published runtime dependency, so it cannot borrow a version from a BOM that is
  deliberately kept out of consumers' resolution; without the pin it publishes with no `<version>`
  at all. Bump it together with `springBoot`.

## Versioning and release policy

Strict SemVer, with a `v`-prefixed git tag as the **single source of truth for the version**.

```
v0.1.0-RC1   ->  0.1.0-RC1     release candidate
v0.1.0       ->  0.1.0         release
v0.1.1       ->  0.1.1         patch
```

- **No commit ever carries a release version.** `gradle.properties` stays on `0.1.0-SNAPSHOT` for
  local work; the release workflow passes `-Pversion=<tag minus v>`, which overrides it. Nothing has
  to be committed, bumped or reverted to cut a release.
- `release.yml` enforces `^v[0-9]+\.[0-9]+\.[0-9]+(-RC[0-9]+)?$` and fails on anything else, so a
  typo cannot become a permanent Central version. The `RC` is case sensitive.
- `RC` is a qualifier both Maven's `ComparableVersion` and Gradle's version ordering rank *below*
  the matching release, so `0.1.0-RC1 < 0.1.0` and a candidate never wins a version comparison.
- **Pre-1.0, breaking changes may land in a minor bump.** From `1.0.0` on, strict SemVer.
- **No snapshots are published.** Tags are the only publish trigger. `test-application` depends on
  `project(":configmap-watcher")`, so local development never needs one.

Cutting a release: update `CHANGELOG.md`, tag `v0.1.0-RC1`, let it through both gates, iterate as
`-RC2`/`-RC3` if needed, then tag `v0.1.0`. Patches may skip the RC step.

## Publishing

Published to **Maven Central** as `io.github.quicklybly:configmap-watcher` — four artifacts (jar,
`-sources`, `-javadoc`, POM) plus Gradle module metadata, driven by
`com.vanniktech.maven.publish` in `configmap-watcher/build.gradle.kts`.

- **Two manual gates stand between a tag and a public release, by design.** Gate 1 is the
  `maven-central` GitHub Environment, whose required reviewers hold the `publish` job. Gate 2 is
  Central's own: `publishToMavenCentral()` is called *without* `automaticRelease`, so the upload
  validates and then waits in the Portal until a human clicks Publish. Adding
  `automaticRelease = true` would remove the last stop before something permanent and undeletable.
- **A `VALIDATED` deployment can be dropped**, which is why gate 2 is worth more than gate 1: by the
  time you decide, you already know the artifacts built, signed and passed Central's validation.
  This is also how to dry-run the whole pipeline — tag an RC, let it upload, then drop it.
- **Gate 1 is inert while the repo is private.** Environment protection rules need GitHub Pro on a
  private user-account repo. Without them GitHub silently auto-creates the environment *without*
  rules and the job runs straight through — no error, no pause. It starts working when the repo goes
  public, with no workflow change.
- **`signAllPublications()` is guarded on `signingInMemoryKey` being present.** Unconditional
  signing breaks `publishToMavenLocal` for anyone without a GPG key. CI always has the key, and
  `release.yml` checks all four secrets up front so a missing one fails there rather than at Central.
- **Dokka runs in V2 mode**, which is the default from 2.1.0 - hence the task name
  `dokkaGeneratePublicationHtml`, which is handed to `JavadocJar.Dokka(...)`. Under 2.0.0 the plugin
  silently falls back to V1, where that task does not exist and the build fails. The publish plugin
  dropped Dokka v1 support entirely, so V2 is not optional here.
- **The POM metadata blocks (`name`, `description`, `url`, `licences`, `developers`, `scm`) exist because Maven Central
  rejects bundles without them**, not because anything reads them at build time. `scm` mirrors the `origin` remote.
- **`compileOnly` dependencies are deliberately absent from the POM** - consumers bring their own Spring. Only
  `kotlin-stdlib`, `kotlin-logging` and `slf4j-api` are declared. Verify with
  `./gradlew publishToMavenLocal -Pversion=0.1.0-RC1` and read the generated `.pom` and `.module`;
  the `.module` is where a platform leak shows up first.

### One-time setup, outside the repo

Publishing cannot work until these exist; none of them are in version control.

1. A Central Portal account signed in **with GitHub**, which auto-verifies `io.github.quicklybly`.
2. A **user token** generated in the Portal - not the login password.
3. A GPG key whose **public half is pushed to a keyserver** (`keys.openpgp.org` or
   `keyserver.ubuntu.com`). Central validates signatures against it and fails the deployment if the
   key cannot be found.
4. Repository secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`
   (ASCII-armored) and `GPG_PASSPHRASE`.
5. A `maven-central` environment with a required reviewer (see the gate 1 caveat above).
