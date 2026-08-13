# CLAUDE.md - test application module

A real Spring Boot application consuming the library through `project(":configmap-watcher")`, so the
`AutoConfiguration.imports` entry, the conditional bean, the watch service, the config re-read and the rebuilt bean are
all exercised together. Build conventions and commands live in the root
`CLAUDE.md`.

- `TestApplication.kt` - `@SpringBootApplication`, web type `none`.
- `WatchedMessage.kt` - a `@RefreshScope` bean holding a `@Value` from the watched file. Being refresh scoped is what
  makes a refresh observable: the bean is discarded and rebuilt on the next access after `refresh()`.
- `application.yaml` - sets `config-map-watcher.enabled: true`. No config location, so `bootRun`
  logs "config map watcher is disabled" unless one is passed in.
- `spring-cloud-starter` is a dependency because it supplies the `ConfigDataContextRefresher` the library's `@Bean`
  injects. Without it the autoconfiguration cannot start.

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
- **`@RefreshScope` on a Kotlin class needs the `kotlin-spring` plugin.** It opens the class so Spring can create the
  CGLIB scoped proxy. Drop the plugin and the context fails to start:
  `AopConfigException` from `CglibAopProxy`, because `Enhancer` cannot subclass a final class.
- The 30s awaitility budget is deliberate - see the macOS polling note in the library module's
  `CLAUDE.md`.
