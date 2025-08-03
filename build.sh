#!/bin/bash
# Railway Build Script for ConnectHub

echo "🚀 Building ConnectHub for Railway deployment..."

# Ensure UTF-8 encoding
export LANG=C.UTF-8
export LC_ALL=C.UTF-8

# Create necessary directories
mkdir -p uploads downloads logs

# Set directory permissions
chmod 755 uploads downloads logs

# Compile Java files
echo "📦 Compiling Java files..."
javac -encoding UTF-8 -cp ".:postgresql-42.7.7.jar" *.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    
    # Clean up compiled files (Docker will recompile)
    rm -f *.class
    
    echo "🎉 ConnectHub is ready for Railway deployment!"
    exit 0
else
    echo "❌ Compilation failed!"
    exit 1
fi
