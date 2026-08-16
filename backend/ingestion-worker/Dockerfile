FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Resolve dependencies in their own layer, from just the build files, before the full source is
# copied in. Otherwise a single `COPY . .` + `RUN ./gradlew ...` means any source change anywhere
# in the repo (even in an unrelated service) busts the cache and forces a full dependency
# re-download from Maven Central on every build — across four services building in parallel, that
# reliably trips Maven Central's rate limiting.
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY common/build.gradle.kts common/build.gradle.kts
COPY gateway-service/build.gradle.kts gateway-service/build.gradle.kts
COPY document-service/build.gradle.kts document-service/build.gradle.kts
COPY ingestion-worker/build.gradle.kts ingestion-worker/build.gradle.kts
COPY query-service/build.gradle.kts query-service/build.gradle.kts
RUN ./gradlew --no-daemon :ingestion-worker:dependencies

COPY . .
RUN ./gradlew --no-daemon :ingestion-worker:bootJar

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN apk upgrade --no-cache
RUN addgroup -S documind && adduser -S documind -G documind
COPY --from=build /workspace/ingestion-worker/build/libs/*.jar app.jar
USER documind
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
