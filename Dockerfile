# Use OpenJDK 17 for better compatibility
FROM openjdk:17-jre-slim

# Set working directory
WORKDIR /app

# Install required tools
RUN apt-get update && \
    apt-get install -y wget curl && \
    rm -rf /var/lib/apt/lists/*

# Copy PostgreSQL JDBC driver
COPY postgresql-42.7.7.jar /app/

# Copy Java source files
COPY *.java /app/

# Copy configuration and database files
COPY config/ /app/config/
COPY database/ /app/database/

# Create necessary directories with proper permissions
RUN mkdir -p uploads downloads logs && \
    chmod 755 uploads downloads logs

# Compile Java files with UTF-8 encoding
RUN javac -encoding UTF-8 -cp ".:postgresql-42.7.7.jar" *.java

# Expose the port (Railway will set this dynamically)
EXPOSE 8888

# Health check - check if the server port is responding
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8888} || exit 1

# Run the Enhanced Server
CMD ["sh", "-c", "java -cp .:postgresql-42.7.7.jar EnhancedServer"]