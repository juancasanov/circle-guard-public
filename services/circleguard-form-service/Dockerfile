FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon :services:circleguard-form-service:dependencies
COPY . .
RUN chmod +x ./gradlew && ./gradlew :services:circleguard-form-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/services/circleguard-form-service/build/libs/*.jar app.jar
EXPOSE 8086
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
