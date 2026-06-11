# ARCHITECTURE DECISION: Ubuntu-based temurin images instead of -alpine.
# The alpine variants of eclipse-temurin 17 are published for amd64 only,
# which breaks `docker-compose up` on ARM hosts (Apple Silicon). The Ubuntu
# images are multi-arch; the size difference is irrelevant for a dev setup.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
# Warm the dependency cache in its own layer so source edits don't re-download
RUN ./gradlew dependencies --no-daemon -q
COPY src src
RUN ./gradlew bootJar --no-daemon -q

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
