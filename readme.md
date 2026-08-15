# configmap watcher

Spring Boot autoconfiguration that watches Kubernetes ConfigMap mount paths and refreshes the application context.

## Usage

```kotlin
dependencies {
    implementation("io.github.quicklybly:configmap-watcher:<version>")
}
```

The watcher is off unless you turn it on, and it watches whatever
`spring.config.additional-location` points at:

```yaml
config-map-watcher:
    enabled: true

spring:
    config:
        additional-location: /etc/config/application.yaml
```

Run it against a real cluster with [`k8s/up.sh`](k8s/README.md).
