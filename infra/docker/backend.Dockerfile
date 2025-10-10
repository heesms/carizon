# syntax=docker/dockerfile:1.6
FROM gradle:8.7-jdk17-alpine AS build
WORKDIR /workspace
COPY backend ./backend
WORKDIR /workspace/backend
RUN gradle :api:bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
ENV JAVA_OPTS=""
WORKDIR /app
COPY --from=build /workspace/backend/api/build/libs/carizon-api.jar ./carizon-api.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar carizon-api.jar"]
