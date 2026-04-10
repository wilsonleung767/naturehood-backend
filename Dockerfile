# ─── Stage 1: Build ───────────────────────────────────────────────────────────
# Use the official Maven image that bundles Java 21 (Eclipse Temurin).
# This stage downloads dependencies and compiles the fat JAR.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy dependency descriptors first so Docker can cache the dependency-download
# layer independently from source changes.
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd ./

# Pre-fetch all dependencies (layer is cached as long as pom.xml is unchanged).
RUN mvn dependency:go-offline -q

# Now copy the source tree and build the fat JAR, skipping tests.
# Tests should be run in a separate CI step, not inside the Docker build.
COPY src/ src/

RUN mvn package -DskipTests -q

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Use a minimal JRE-only image to keep the final image small and reduce attack surface.
FROM eclipse-temurin:21-jre-alpine AS runtime

# Create a non-root user for the app process.
RUN addgroup -S naturehood && adduser -S naturehood -G naturehood

WORKDIR /app

# Copy only the built fat JAR from the builder stage.
COPY --from=builder /build/target/naturehood_backend-*.jar app.jar

# Set ownership so the non-root user can read the JAR.
RUN chown naturehood:naturehood app.jar

USER naturehood

EXPOSE 8080

# JVM tuning for containerised environments:
#   -XX:+UseContainerSupport          respect cgroup memory/CPU limits
#   -XX:MaxRAMPercentage=75.0         use up to 75 % of the container's RAM for the heap
#   -Djava.security.egd=...           faster SecureRandom startup on Linux
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
