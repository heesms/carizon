# Carizon - Used Car Aggregation Platform

Full-stack application for aggregating and analyzing used car listings across multiple platforms.

## 🚀 Tech Stack

### Backend
- **Spring Boot 3** (Java 21)
- **Gradle** build system
- **Flyway** for database migrations
- **MySQL 8** for persistent storage
- **Redis 7** for caching
- **JPA/Hibernate** for ORM
- **Swagger/OpenAPI** for API documentation

### Frontend
- **React 18** with TypeScript
- **Vite** for build tooling
- **Tailwind CSS** for styling
- **React Query** for data fetching
- **Zustand** for state management
- **Recharts** for data visualization
- **React Router** for navigation

### Infrastructure
- **Docker Compose** for local development
- **MySQL 8** database
- **Redis 7** cache
- **Meilisearch** for search (integration pending)
- **GitHub Actions** for CI/CD

## 📁 Project Structure

```
carizon/
├── backend/              # Spring Boot API
│   ├── src/main/java/com/carizon/
│   │   ├── api/         # New API layer (entities, repos, services, controllers)
│   │   ├── config/      # Security, Redis, OpenAPI configs
│   │   ├── crawler/     # Platform crawlers
│   │   ├── mapping/     # Code mapping services
│   │   └── merge/       # Data merge services
│   ├── src/main/resources/
│   │   ├── db/migration/  # Flyway SQL migrations
│   │   └── application.yaml
│   ├── build.gradle     # Gradle build file
│   └── gradlew         # Gradle wrapper
├── frontend/            # React + Vite app
│   ├── src/
│   │   ├── api/        # API client
│   │   ├── components/ # React components
│   │   ├── pages/      # Page components
│   │   └── App.jsx
│   ├── package.json
│   └── vite.config.js
├── infra/              # Infrastructure configs
│   ├── docker-compose.yml
│   └── .env.example
├── .github/workflows/  # CI/CD pipelines
│   ├── backend.yml
│   └── frontend.yml
├── README.md
└── usage.md           # Detailed usage guide

## 🎯 Features

- **Multi-platform aggregation**: Collect car listings from ENCAR, CHACHACHA, KCAR, etc.
- **Price tracking**: Historical price data with visualizations
- **Advanced search**: Filter by maker, model, year, mileage, region, etc.
- **Normalized data**: Standardized 5-level code mapping (maker → model group → model → trim → grade)
- **RESTful API**: Well-documented API with Swagger UI
- **Responsive UI**: Mobile-friendly interface with Tailwind CSS
- **Real-time updates**: React Query for efficient data fetching

## 🚦 Quick Start

See [usage.md](usage.md) for detailed setup instructions.

### Prerequisites
- Docker & Docker Compose
- Java 21
- Node.js 20+

### Start Services

1. **Start infrastructure:**
   ```bash
   cd infra
   docker-compose up -d
   ```

2. **Start backend:**
   ```bash
   cd backend
   ./gradlew bootRun
   ```

3. **Start frontend:**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

4. **Access:**
   - Frontend: http://localhost:5173
   - Backend API: http://localhost:8080/api/cars
   - Swagger UI: http://localhost:8080/swagger-ui.html

## 📊 Database Schema

### Core Tables

- **car_master**: Normalized master ledger of unique vehicles
- **platform_car**: Source listings per platform (ENCAR, KCAR, etc.)
- **car_price_history**: Time-series price data per platform listing
- **platform_code_mapping**: Raw to standard code mappings

See `backend/src/main/resources/db/migration/V1__init.sql` for complete schema.

## 🔌 API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/cars` | GET | List/search cars with filters and pagination |
| `/api/cars/{id}` | GET | Get car detail with platform listings |
| `/api/cars/{id}/price-history` | GET | Get price history for a car |

Full API documentation: http://localhost:8080/swagger-ui.html

## 🧪 Testing

Run backend tests:
```bash
cd backend
./gradlew test
```

Run frontend tests:
```bash
cd frontend
npm test
```

## 🏗️ Building

**Backend:**
```bash
cd backend
./gradlew build
```

**Frontend:**
```bash
cd frontend
npm run build
```

## 📝 Development Roadmap

- [x] Initial scaffolding and core schema
- [x] Flyway migrations with seed data
- [x] RESTful API endpoints
- [x] React frontend with search and detail pages
- [x] Price history visualization
- [ ] Meilisearch integration for full-text search
- [ ] JWT authentication and RBAC
- [ ] Advanced filtering and sorting
- [ ] Email notifications for price drops
- [ ] Mobile app (React Native)

## 📖 Documentation

- [usage.md](usage.md) - Complete usage guide with troubleshooting
- [Swagger UI](http://localhost:8080/swagger-ui.html) - Interactive API documentation

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License.

## 👥 Team

Carizon Development Team

---

**Note**: This is an active development project. Some features are still being implemented (marked as TODO in the codebase).