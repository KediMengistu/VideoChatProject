# (no syntax line)
ARG JAVA_VERSION=21

FROM maven:3.9.9-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:${JAVA_VERSION}-jre
ENV TZ=UTC
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
RUN useradd -r -u 10001 spring
USER spring
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-XX:+UseZGC","-jar","/app/app.jar"]
