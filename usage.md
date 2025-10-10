# Carizon Usage Guide

Complete guide for running Carizon locally, testing, and troubleshooting.

## Prerequisites

- **Docker & Docker Compose** (for MySQL, Redis, Meilisearch)
- **Java 21** (for backend)
- **Node.js 20+** (for frontend)
- **Git**

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/heesms/carizon.git
cd carizon
```

### 2. Start Infrastructure Services

Start MySQL, Redis, and Meilisearch using Docker Compose:

```bash
cd infra
docker-compose up -d
```

Verify services are running:

```bash
docker-compose ps
```

You should see:
- `carizon-mysql` on port 3306
- `carizon-redis` on port 6379
- `carizon-meilisearch` on port 7700

### 3. Start Backend (Spring Boot)

The backend will automatically:
- Connect to MySQL
- Run Flyway migrations (V1__init.sql, V2__seed.sql)
- Seed sample data

```bash
cd backend
./gradlew bootRun
```

Backend will start on **http://localhost:8080**

Check the API:
```bash
curl http://localhost:8080/api/cars
```

Swagger UI available at: **http://localhost:8080/swagger-ui.html**

### 4. Start Frontend (React + Vite)

```bash
cd frontend
npm install
npm run dev
```

Frontend will start on **http://localhost:5173**

The `/api` requests are automatically proxied to the backend (localhost:8080).

## API Endpoints

### Get Cars List
```bash
curl "http://localhost:8080/api/cars?page=0&size=20"
```

With filters:
```bash
curl "http://localhost:8080/api/cars?maker=HYN&modelGroup=SONATA&page=0&size=20"
```

### Get Car Detail
```bash
curl "http://localhost:8080/api/cars/1"
```

### Get Price History
```bash
curl "http://localhost:8080/api/cars/1/price-history"
```

Filter by specific platform car:
```bash
curl "http://localhost:8080/api/cars/1/price-history?platformCarId=1"
```

## Viewing Logs

### Docker Compose Services

View all service logs:
```bash
cd infra
docker-compose logs -f
```

View specific service:
```bash
docker-compose logs -f mysql
docker-compose logs -f redis
docker-compose logs -f meilisearch
```

### Spring Boot Backend

Logs are output to console by default. To adjust log levels, edit `backend/src/main/resources/application.yaml`:

```yaml
logging:
  level:
    root: INFO
    com.carizon: DEBUG
    org.springframework.jdbc: DEBUG
```

Enable SQL logging:
```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

### Frontend (Vite)

Vite dev server logs are shown in the terminal. Browser console shows runtime logs.

## Database Access

Connect to MySQL:

```bash
docker exec -it carizon-mysql mysql -u carizon -p
# Password: carizon!1
```

Or use any MySQL client:
- Host: `localhost`
- Port: `3306`
- Database: `carizon`
- User: `carizon`
- Password: `carizon!1`

Useful queries:

```sql
-- Check migrations
SELECT * FROM flyway_schema_history;

-- Check car_master data
SELECT * FROM car_master LIMIT 10;

-- Check platform_car data
SELECT * FROM platform_car LIMIT 10;

-- Check price history
SELECT * FROM car_price_history ORDER BY checked_at DESC LIMIT 20;
```

## Testing

### Backend Tests

Run all tests:
```bash
cd backend
./gradlew test
```

Run tests with coverage:
```bash
./gradlew test jacocoTestReport
```

Run specific test class:
```bash
./gradlew test --tests CarQueryServiceTest
```

### Frontend Tests

```bash
cd frontend
npm test
```

## Build for Production

### Backend

Build JAR:
```bash
cd backend
./gradlew clean build
```

Output: `backend/build/libs/carizon-api-0.0.1-SNAPSHOT.jar`

Run JAR:
```bash
java -jar build/libs/carizon-api-0.0.1-SNAPSHOT.jar
```

### Frontend

Build static assets:
```bash
cd frontend
npm run build
```

Output: `frontend/dist/`

Preview production build:
```bash
npm run preview
```

## Environment Configuration

### Backend

Copy `.env.example` and customize:

```bash
cd infra
cp .env.example .env
```

Edit `backend/src/main/resources/application.yaml` for different profiles:

```yaml
spring:
  profiles:
    active: dev  # or test, prod
```

### Frontend

Create `.env.local` (optional):

```bash
cd frontend
touch .env.local
```

Add custom environment variables:
```
VITE_API_BASE_URL=http://localhost:8080
```

## Troubleshooting

### Port Already in Use

If ports 3306, 6379, 7700, 8080, or 5173 are already in use:

1. **Find process using the port:**
   ```bash
   lsof -i :8080  # or any other port
   ```

2. **Kill the process:**
   ```bash
   kill -9 <PID>
   ```

3. **Or change the port in configuration**

### Flyway Migration Errors

If Flyway fails to migrate:

1. **Check MySQL is running:**
   ```bash
   docker-compose ps mysql
   ```

2. **Verify database connection:**
   ```bash
   docker exec -it carizon-mysql mysql -u carizon -p carizon
   ```

3. **Reset Flyway (CAUTION: drops all data):**
   ```sql
   DROP DATABASE carizon;
   CREATE DATABASE carizon CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
   ```

4. **Restart backend to re-run migrations**

### Charset/Collation Issues

Ensure MySQL is using utf8mb4:

```sql
SHOW VARIABLES LIKE 'char%';
SHOW VARIABLES LIKE 'collation%';
```

If incorrect, restart MySQL with correct configuration:

```bash
docker-compose down
docker-compose up -d
```

### Timezone Issues

Application timezone is set to `Asia/Seoul`. Verify:

**Backend:**
```java
// TimeZoneConfig.java sets default timezone
TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
```

**MySQL:**
```sql
SELECT @@global.time_zone, @@session.time_zone;
```

**Docker Compose:**
```yaml
environment:
  TZ: Asia/Seoul
```

### Redis Connection Issues

Test Redis connection:

```bash
docker exec -it carizon-redis redis-cli
> PING
PONG
```

Clear Redis cache:
```bash
docker exec -it carizon-redis redis-cli FLUSHALL
```

### Backend Won't Start

1. **Check Java version:**
   ```bash
   java -version  # Should be 21
   ```

2. **Clean and rebuild:**
   ```bash
   cd backend
   ./gradlew clean build
   ```

3. **Check logs for specific errors**

### Frontend Build Errors

1. **Clear node_modules and reinstall:**
   ```bash
   cd frontend
   rm -rf node_modules package-lock.json
   npm install
   ```

2. **Check Node version:**
   ```bash
   node -v  # Should be 20+
   ```

3. **Verify Vite config is correct**

## Development Tips

### Auto-restart Backend on Changes

Use Spring Boot DevTools (already included):

Changes to Java code will trigger automatic restart.

### Hot Module Replacement (HMR) for Frontend

Vite HMR is enabled by default. Changes to React components update instantly.

### Database Seed Data

Sample data is automatically loaded via `V2__seed.sql`. To reset:

```bash
# Stop backend
# Drop and recreate database
docker exec -it carizon-mysql mysql -u root -p
> DROP DATABASE carizon;
> CREATE DATABASE carizon CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
> exit

# Restart backend - migrations will run again
```

### Meilisearch Integration (TODO)

Meilisearch is running but not yet integrated. Future enhancement:

1. Index car_master data to Meilisearch
2. Use Meilisearch for full-text search
3. Update CarQueryService to use Meilisearch

## CI/CD

GitHub Actions workflows are configured:

- **backend.yml**: Builds backend with Gradle, runs tests
- **frontend.yml**: Builds frontend with npm

Workflows run on push/PR to `main` or `develop` branches.

## Additional Resources

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Actuator Health**: http://localhost:8080/actuator/health
- **Meilisearch Dashboard**: http://localhost:7700

## Support

For issues or questions:
1. Check this guide first
2. Review logs (Docker, backend, frontend)
3. Open an issue on GitHub

---

**Last Updated**: 2025-10-10
