FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend-java/pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY backend-java/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app/backend-java
COPY --from=build /build/target/metrics-calculator-1.0.0.jar app.jar
COPY sample-data /app/sample-data
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx2g", "-jar", "app.jar"]
