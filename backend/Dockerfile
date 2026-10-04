FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src/main ./src/main
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar --console=plain

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /build/build/libs/backend-0.0.1-SNAPSHOT.jar ./app.jar
ENV SPRING_PROFILES_ACTIVE=prod SERVER_PORT=8080 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 8080
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
