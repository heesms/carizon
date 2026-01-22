@echo off
echo ========================================
echo Carizon Admin - Production Build
echo ========================================
echo.

echo [1/3] Building frontend...
cd admin-frontend
if not exist node_modules (
    echo Installing dependencies...
    call npm install
)
call npm run build
if errorlevel 1 (
    echo Frontend build failed!
    exit /b 1
)
echo Frontend build completed.
echo.

echo [2/3] Copying frontend to Spring Boot static resources...
if exist ..\src\main\resources\static (
    rmdir /S /Q ..\src\main\resources\static
)
mkdir ..\src\main\resources\static
xcopy /E /I /Y dist\* ..\src\main\resources\static\
echo Frontend files copied.
echo.

echo [3/3] Building Spring Boot...
cd ..
call mvn clean package -DskipTests
if errorlevel 1 (
    echo Spring Boot build failed!
    exit /b 1
)
echo.

echo ========================================
echo Build completed successfully!
echo JAR file: target\backend-0.0.1-SNAPSHOT.jar
echo ========================================
pause
