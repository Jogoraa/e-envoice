# Multi-stage Dockerfile for UT Electronic Invoicing Platform
# Stage 1: Build application with Maven and Java 21
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Production runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache chromium font-noto-ethiopic ttf-dejavu && \
    addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=build /app/target/ut-einvoice-platform-1.0.0-RELEASE.jar app.jar

USER appuser:appgroup
EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
