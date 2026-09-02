# ============================================================
# SentinelCore SecureOps - Render Dockerfile
# Spring Boot 3.4.7 / Java 17
# Optimized for Render Free 512 MB RAM
# ============================================================

# ------------------------------------------------------------
# Stage 1: Build
# ------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# Copy Maven wrapper and project configuration first
# so Docker can cache dependencies between builds.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Make Maven wrapper executable
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -q

# Copy application source
COPY src src

# Build Spring Boot application
RUN ./mvnw clean package -DskipTests -q


# ------------------------------------------------------------
# Stage 2: Runtime
# ------------------------------------------------------------
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the generated Spring Boot JAR
COPY --from=build /app/target/dashboard-0.0.1-SNAPSHOT.jar app.jar

# Render supplies the PORT environment variable.
# 8080 is used as a local/default fallback.
EXPOSE 8080

# ------------------------------------------------------------
# Start Spring Boot
#
# Memory optimized for Render Free:
#   Heap              = 192 MB
#   Metaspace         = 96 MB
#   Direct memory     = 32 MB
#   Code cache        = 32 MB
#   Serial GC         = lower memory overhead
#   Thread stack      = 256 KB
# ------------------------------------------------------------
ENTRYPOINT ["sh", "-c", "exec java -Xms64m -Xmx192m -XX:MaxMetaspaceSize=96m -XX:MaxDirectMemorySize=32m -XX:ReservedCodeCacheSize=32m -XX:+UseSerialGC -Xss256k -Dserver.port=${PORT:-8080} -jar app.jar"]