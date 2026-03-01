FROM gradle:8.14.3-jdk24 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon clean bootJar

FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
