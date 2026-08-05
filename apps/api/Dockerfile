FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S medrag && adduser -S -G medrag -u 10001 medrag
WORKDIR /app
COPY --from=build /workspace/target/medrag-api-*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseG1GC", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
