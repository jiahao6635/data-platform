FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml ./
RUN mvn -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /build/target/data-platform-backend-*.jar /app/application.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/application.jar"]

