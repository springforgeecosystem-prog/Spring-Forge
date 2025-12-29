# Stage 1: Build Plugin
FROM eclipse-temurin:21-jdk-alpine AS builder

# Install bash and dos2unix for line ending conversion
RUN apk add --no-cache bash dos2unix

# Set working directory
WORKDIR /build

# Copy Gradle wrapper files
COPY gradlew ./
COPY gradlew.bat ./
COPY gradle/ ./gradle/

# Copy build configuration
COPY build.gradle.kts ./
COPY settings.gradle.kts ./

# Copy source code
COPY src/ ./src/

# Fix line endings and make Gradle wrapper executable
RUN dos2unix gradlew && chmod +x ./gradlew

# Buildthe plugin
RUN ./gradlew buildPlugin --no-daemon

# Verify build output
RUN ls -la /build/build/distributions/

# Stage 2: Distribution
FROM alpine:3.18

# Create non-root user
RUN adduser -D -u 1000 appuser

# Set working directory
WORKDIR /plugin

# Copy plugin distribution files
COPY --from=builder --chown=appuser:appuser /build/build/distributions/*.zip ./

# Switch to non-root user
USER appuser

# Add labels
LABEL version="1.0.0"
LABEL name="SpringForge-CodeGeneration"
LABEL type="intellij-plugin"
LABEL java.version="21"

# Default command to list plugin files
CMD ["ls", "-lah", "/plugin"]