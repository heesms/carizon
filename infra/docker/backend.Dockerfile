# syntax=docker/dockerfile:1.6
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY backend ./backend
WORKDIR /workspace/backend
RUN mvn -B -pl api -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
ENV JAVA_OPTS=""
WORKDIR /app
COPY --from=build /workspace/backend/api/target/api-*.jar ./carizon-api.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar carizon-api.jar"]
