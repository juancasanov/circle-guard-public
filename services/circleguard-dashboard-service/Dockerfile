FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon :services:circleguard-dashboard-service:dependencies
COPY . .
RUN chmod +x ./gradlew && ./gradlew :services:circleguard-dashboard-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/services/circleguard-dashboard-service/build/libs/*.jar app.jar
EXPOSE 8084
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
