# --- Build stage -------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copy only the POM first so dependency resolution is cached across
# source-only changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:17-jre

# Run as a non-root user rather than the image default (root).
RUN addgroup --system app && adduser --system --ingroup app app
USER app

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
