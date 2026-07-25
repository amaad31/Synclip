FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/
COPY sync-engine/pom.xml sync-engine/
COPY clipboard/pom.xml clipboard/
COPY common/src common/src
COPY sync-engine/src sync-engine/src
COPY clipboard/src clipboard/src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/sync-engine/target/sync-engine-1.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]