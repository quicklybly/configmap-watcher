#   docker build -f test-application/Dockerfile -t configmap-watcher-test-app:local .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY configmap-watcher/build.gradle.kts configmap-watcher/
COPY test-application/build.gradle.kts test-application/

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :test-application:dependencies

COPY configmap-watcher/src configmap-watcher/src
COPY test-application/src test-application/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :test-application:bootJar \
    && cp test-application/build/libs/test-application.jar /workspace/app.jar

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
