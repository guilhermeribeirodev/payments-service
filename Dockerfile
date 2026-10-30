# syntax=docker/dockerfile:1
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN gradle --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN gradle --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY --from=build /src/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s CMD ["bash","-c","exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/liveness HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q UP <&3"]
ENTRYPOINT ["java","-jar","/app/app.jar"]
