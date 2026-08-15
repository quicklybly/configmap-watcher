# CLAUDE.md - test application module

A real Spring Boot application consuming the library through `project(":configmap-watcher")`, so the
`AutoConfiguration.imports` entry, the conditional bean, the watch service, the config re-read and the rebuilt bean are
all exercised together. Build conventions and commands live in the root
`CLAUDE.md`.

- `TestApplication.kt` - `@SpringBootApplication` and `@EnableScheduling`, the latter only for the reporter's heartbeat.
- `ConfigMapProperties.kt` - a constructor bound `@ConfigurationProperties` bean holding the value from the watched
  file, registered by `@EnableConfigurationProperties` on `TestApplication`. It is `@RefreshScope` too, which is what
  makes a refresh observable: the bean is discarded and rebuilt on the next access after `refresh()`.
- `MessageController.kt` - `GET /message`, so a pod's current value can be read from outside the JVM. Also the
  deployment's readiness probe target, which is why there is no actuator dependency.
- `MessageReporter.kt` - logs the value, so `kubectl logs -f` shows a refresh happening.
- `application.yaml` - sets `config-map-watcher.enabled: true`. No config location, so `bootRun`
  logs "config map watcher is disabled" unless one is passed in.
- `spring-cloud-starter` is a dependency because it supplies the `ConfigDataContextRefresher` the library's `@Bean`
  injects. Without it the autoconfiguration cannot start.

The app serves HTTP: it is deployed to a real cluster by `k8s/` (see `k8s/README.md`), where the only way to observe a
running pod is over the network. `spring.main.web-application-type` is therefore left at its default - do not set it
back to `none`.

## Traps

- **The config location must be a system property or environment variable, never
  `@SpringBootTest(properties = ...)`.** Spring Cloud's `ConfigDataContextRefresher` rebuilds the environment from the
  standard sources only, so a `spring.config.additional-location` supplied as an inlined test property is gone by the
  time the config is re-read: the file changes, the watcher fires, `refresh()` returns no changed keys, and the bean
  keeps its old value. The failure looks like a broken watcher but the watcher is fine. `ConfigMapRefreshE2ETest` sets a
  system property in
  `@BeforeAll` and clears it in `@AfterAll`, which is also how a pod passes the location in.
- **Seeding must happen in `@BeforeAll`.** It runs before the test instance is created and therefore before the
  application context loads, so the file exists when Spring Boot resolves the location.
  `@BeforeEach` would be too late.
- **`ConfigMapProperties` uses constructor injection, which needs four things at once.** Each was a separate failure
  on the way in, and removing any one of them brings its failure back:
    - **Defaults come from `@DefaultValue`, never a Kotlin default.** Kotlin also emits a synthetic no-args
      constructor when *every* parameter has a default, so Spring sees two constructors, cannot infer constructor
      binding, falls back to JavaBean binding and fails with `No setter found for property: message`. Reaching for
      `@ConstructorBinding` does not help - it lands on that synthetic constructor and fails with
      `declares @ConstructorBinding on a no-args constructor`.
    - **`kotlin-reflect` on the classpath.** Constructor binding on a Kotlin class needs it to discover parameter
      names; without it the context fails with `NoClassDefFoundError: kotlin/reflect/jvm/ReflectJvmMapping`.
    - **`@RefreshScope`.** `ConfigurationPropertiesRebinder` refreshes a bean by running `destroyBean` then
      `initializeBean` on the *same instance*, which can only re-bind setters. A constructor bound bean is not
      re-bound in place: the value silently stays stale, and the rebinder logs
      `No argument provided for a required parameter`. Refresh scope sidesteps it by rebuilding the bean.
    - **The `allOpen` entry for `@RefreshScope` in `build.gradle.kts`.** `kotlin-spring` opens `@Component` and
      friends but not `@RefreshScope`, and the CGLIB scoped proxy has to intercept the getter. Marking the class
      `open` by hand is not enough - Kotlin members stay final, so the proxy reads its own uninitialised field and
      the property comes back `null`.
- The 30s awaitility budget is deliberate - see the macOS polling note in the library module's
  `CLAUDE.md`.
- **`bootJar` has a fixed `archiveFileName`** because `Dockerfile` copies that exact name. A glob would also match the
  `-plain` jar the `jar` task produces next to it.
- **`Dockerfile` lives here but its build context is the repository root**, since the module depends on
  `project(":configmap-watcher")` and needs the wrapper, the settings script and the version catalog too. Build it with
  `docker build -f test-application/Dockerfile .` from the root, or just use `k8s/up.sh`.
