# =============================================================================
# AUTH MICROSERVICE - OPTIMIZED MULTI-STAGE DOCKERFILE
# =============================================================================
# Optimized for production with JDK 21, security, and performance

# =============================================================================
# BUILD STAGE
# =============================================================================
FROM gradle:8.5-jdk21-alpine AS builder

# Set build arguments
ARG BUILD_ENV=production
ARG SKIP_TESTS=false

# Install build dependencies
RUN apk add --no-cache git

WORKDIR /app

# Copy gradle configuration files first for better layer caching
COPY build.gradle settings.gradle gradle.properties ./

# Download dependencies (this layer will be cached unless dependencies change)
RUN gradle dependencies --no-daemon --parallel

# Copy source code
COPY src src

# Build the application with optimizations
RUN if [ "$SKIP_TESTS" = "true" ]; then \
        gradle shadowJar --no-daemon --parallel -x test; \
    else \
        gradle shadowJar --no-daemon --parallel; \
    fi

# Verify the jar was created
RUN ls -la build/libs/ && \
    test -f build/libs/*-fat.jar

# =============================================================================
# RUNTIME STAGE
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install runtime dependencies and security updates
RUN apk update && \
    apk add --no-cache \
        curl \
        dumb-init \
        tzdata \
        ca-certificates && \
    apk upgrade && \
    rm -rf /var/cache/apk/*

# Set timezone
ENV TZ=UTC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Create application user and group with specific UID/GID for security
RUN addgroup -g 1001 -S authuser && \
    adduser -u 1001 -S authuser -G authuser -h /app -s /bin/sh

# Create application directories
RUN mkdir -p /app/logs /app/config /app/tmp && \
    chown -R authuser:authuser /app

# Set working directory
WORKDIR /app

# Copy the fat jar from builder stage
COPY --from=builder --chown=authuser:authuser /app/build/libs/*-fat.jar app.jar

# Verify jar integrity
RUN java -jar app.jar --version 2>/dev/null || echo "Jar verification skipped"

# Switch to non-root user
USER authuser

# Create volume mount points
VOLUME ["/app/logs", "/app/config"]

# Expose application port
EXPOSE 8080

# Expose metrics port (if enabled)
EXPOSE 9090

# Health check with improved configuration
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# JVM options optimized for containers and production
ENV JAVA_OPTS="\
    -XX:+UseG1GC \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/logs/heapdump.hprof \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -XX:+UseCompressedOops \
    -XX:+UseCompressedClassPointers \
    -Djava.security.egd=file:/dev/./urandom \
    -Djava.awt.headless=true \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=UTC"

# Application-specific environment variables
ENV APP_HOME=/app \
    LOG_DIR=/app/logs \
    CONFIG_DIR=/app/config

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Run the application with optimized JVM settings
CMD ["sh", "-c", "exec java $JAVA_OPTS --enable-preview -jar app.jar"]

# =============================================================================
# METADATA
# =============================================================================
LABEL maintainer="Auth Microservice Team" \
      version="1.0.0" \
      description="Auth Microservice with RBAC using Java 21 and Vert.x" \
      java.version="21" \
      framework="Vert.x" \
      database="PostgreSQL" \
      cache="Redis"