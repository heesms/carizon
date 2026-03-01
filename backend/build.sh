#!/bin/bash

echo "========================================"
echo "Carizon Admin - Production Build"
echo "========================================"
echo ""

echo "[1/3] Building frontend..."
cd admin-frontend
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
fi
npm run build
if [ $? -ne 0 ]; then
    echo "Frontend build failed!"
    exit 1
fi
echo "Frontend build completed."
echo ""

echo "[2/3] Copying frontend to Spring Boot static resources..."
rm -rf ../src/main/resources/static
mkdir -p ../src/main/resources/static
cp -r dist/* ../src/main/resources/static/
echo "Frontend files copied."
echo ""

echo "[3/3] Building Spring Boot..."
cd ..
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "Spring Boot build failed!"
    exit 1
fi
echo ""

echo "========================================"
echo "Build completed successfully!"
echo "JAR file: target/backend-0.0.1-SNAPSHOT.jar"
echo "========================================"
