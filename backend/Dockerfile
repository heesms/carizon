FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /w
COPY pom.xml .
RUN mvn -q -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM gcr.io/distroless/java21-debian12
COPY --from=build /w/target/backend-0.0.1-SNAPSHOT.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
