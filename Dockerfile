# Multi-stage Dockerfile for Campus Nexus
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy Maven wrapper and POM for dependency caching
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build production fat JAR
COPY src ./src
COPY ftp-servers.json ./
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Minimal Production JRE Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl/wget for health checks
RUN apk add --no-cache curl

# Create non-root system user for cyber-security best practices
RUN addgroup -S nexus && adduser -S nexus -G nexus
RUN mkdir -p /app/staging && chown -R nexus:nexus /app

USER nexus

# Copy executable jar and default configuration from builder
COPY --from=builder /build/target/campus-nexus-*.jar app.jar
COPY --from=builder /build/ftp-servers.json /app/ftp-servers.json

ENV PORT=8080 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
