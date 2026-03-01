FROM eclipse-temurin:24-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
