FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon :gateway-service:bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S documind && adduser -S documind -G documind
COPY --from=build /workspace/gateway-service/build/libs/*.jar app.jar
USER documind
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
