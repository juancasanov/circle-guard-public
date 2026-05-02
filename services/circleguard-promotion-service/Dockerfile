FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon :services:circleguard-promotion-service:dependencies
COPY . .
RUN chmod +x ./gradlew && ./gradlew :services:circleguard-promotion-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/services/circleguard-promotion-service/build/libs/*.jar app.jar
EXPOSE 8088
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
