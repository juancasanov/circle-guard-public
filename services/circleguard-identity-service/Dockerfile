FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=docker
COPY services/circleguard-identity-service/build/libs/*[^plain].jar app.jar
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
EXPOSE 8083
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -qO- http://localhost:8083/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
