# CLAUDE.md - library module

The published library. Build conventions, commands and publishing live in the root `CLAUDE.md`.

## Architecture

Two source files carry the design; the rest is wiring.

**`ConfigMapWatcher`** (interface) - the public extension point, extends `AutoCloseable` with a
no-op default `close()`. Implementations must **start themselves** during bean initialisation;
Spring does not detect `@PostConstruct` on interface methods, so nothing in this library calls
`startWatchingConfigMaps()` on a user-supplied bean.

**`FileSystemConfigMapWatcher`** - the default implementation. One `WatchService` per configured
path, each polled by its own virtual thread in a blocking `take()` loop. `close()` closes the watch
services, which unblocks those threads so they exit; it is idempotent and runs from `@PreDestroy`.

**`ConfigMapWatcherConfiguration`** - gated on `config-map-watcher.enabled=true` (off when absent),
registered through `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
Paths come from `spring.config.additional-location`, comma separated, trimmed, empty means disabled.

### Traps

- **The `@Bean` method must declare `: ConfigMapWatcher` explicitly.** `@ConditionalOnMissingBean`
  keys off the declared return type, so letting Kotlin infer `FileSystemConfigMapWatcher` silently
  breaks back-off: a user's own implementation would no longer suppress the default, and both beans
  end up in the context. `ConfigMapWatcherConfigurationTest.keeps the watcher declared by the
  application` is what catches this.
- **Spring dependencies are `compileOnly` and duplicated as `testImplementation`.** Consumers bring
  their own Spring; adding a new Spring dependency means a catalog entry plus both lines in
  `build.gradle.kts`.
- **`refresh()` fires once per watch event, not once per change.** A single write can produce
  several consecutive refreshes; a kubelet symlink swap produces more. Known and uncoalesced.
- **The configured path must be a plain filesystem path.** `watchConfig` does `File(path)`, so a
  `file:` URL prefix - legal in `spring.config.additional-location` - would not resolve. The watch
  is registered on the path's **parent** directory.

## Testing notes

- **macOS has no native file watcher**: the JDK falls back to `PollingWatchService`, which scans
  every 2 seconds (`POLLING_INTERVAL` in the JDK source). Every file-watching test therefore takes
  ~2s and awaitility budgets are 30s on purpose - do not "optimise" them down. Linux uses inotify
  and is immediate.
- **Always close watchers started in a test** (see the `@AfterEach` in `FileSystemConfigMapWatcherTest`).
  Otherwise the threads outlive the test, keep polling a deleted `@TempDir`, and fire refreshes into
  a stale mock.
- **One file per test when asserting which path is watched.** The refresher is a single mock, so a
  test that changes two files cannot distinguish "both paths watched" from "one path watched, two
  events". Hence the separate `watches the first…` / `watches the last…` tests.
- `mockk` for the refresher (relaxed), awaitility for the waits, `ApplicationContextRunner` for the
  auto configuration tests, `@TempDir` for real filesystem behaviour.
- `refreshes context on a kubernetes-style atomic configmap swap` reproduces how kubelet actually
  updates a mount (timestamped dir + `..data` symlink swap, leaf file never written in place) -
  keep that shape when touching it, it is the case the library exists for.
- **These tests stop at "`refresh()` was called on a mock."** Whether a refresh actually reaches the
  application's config is only covered by `test-application`.
