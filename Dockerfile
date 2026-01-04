# depwalk Dockerfile
# Multi-stage build for Go binary + Java helper

# ===== Stage 1: Build Go binary =====
FROM golang:1.23-alpine AS go-builder

WORKDIR /build

# Install build dependencies
RUN apk add --no-cache git

# Copy go mod files first for better caching
COPY go.mod go.sum ./
RUN go mod download

# Copy source code
COPY . .

# Build the Go binary
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o depwalk ./cmd/depwalk

# ===== Stage 2: Build Java helper =====
FROM gradle:8-jdk17 AS java-builder

WORKDIR /build

# Copy Java helper source
COPY java/depwalk-helper ./

# Build fat jar
RUN gradle fatJar --no-daemon

# ===== Stage 3: Final runtime image =====
FROM eclipse-temurin:17-jre-alpine

# Install minimal dependencies
RUN apk add --no-cache ca-certificates

# Create non-root user
RUN adduser -D -h /home/depwalk depwalk
USER depwalk
WORKDIR /home/depwalk

# Copy Go binary
COPY --from=go-builder /build/depwalk /usr/local/bin/depwalk

# Copy Java helper jar
COPY --from=java-builder /build/build/libs/depwalk-helper-*-all.jar /opt/depwalk/depwalk-helper.jar

# Set environment variables
ENV DEPWALK_HELPER_JAR=/opt/depwalk/depwalk-helper.jar
ENV PATH="/usr/local/bin:${PATH}"

# Default working directory for mounted projects
WORKDIR /workspace

ENTRYPOINT ["depwalk"]
CMD ["--help"]

