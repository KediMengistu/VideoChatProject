# syntax=docker/dockerfile:1.7
ARG JAVA_VERSION=21

# ---------- Build stage ----------
FROM maven:3.9.9-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /app

# Copy pom first to leverage dependency cache
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline

# Copy sources and build
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

# ---------- Runtime stage ----------
FROM eclipse-temurin:${JAVA_VERSION}-jre
ENV TZ=UTC
WORKDIR /app

# Copy Spring Boot fat jar
COPY --from=build /app/target/*.jar /app/app.jar

# Run as non-root
RUN useradd -r -u 10001 spring
USER spring

EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-XX:+UseZGC","-jar","/app/app.jar"]
