# Use a multi-stage build to optimize the image size
# ----------------------------------------------------
# Stage 1: Build the application (using a Gradle image)
FROM gradle:8.5-jdk21-alpine AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy only the build.gradle.kts and settings.gradle.kts files first to leverage Docker cache
COPY build.gradle.kts settings.gradle.kts ./

# Copy the source code
COPY src ./src

# Download the dependencies and build the application
RUN gradle build --no-daemon

# ----------------------------------------------------
# Stage 2: Create the production image
FROM debian:stable-slim

# Set the working directory for the production image
WORKDIR /app

RUN apt-get update && apt-get install -y openjdk-17-jdk

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="${PATH}:${JAVA_HOME}/bin"

# Install dcraw and ImageMagick
RUN apt-get update && apt-get install -y --no-install-recommends \
    dcraw \
    imagemagick \
    bash

# Copy the JAR file from the build stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose the port that your Spring Boot application listens on (default is 8080)
EXPOSE 8080

# Set the command to run the Spring Boot application
CMD ["java", "-jar", "app.jar"]
