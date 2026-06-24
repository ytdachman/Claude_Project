# MyApp

Full-stack scaffold: Spring Boot (Java 21) backend + React/Vite/TypeScript frontend.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.9+ (or use `./mvnw`) |
| Node | 18+ |
| PostgreSQL | 15+ |

---

## 1 — Database setup

```sql
CREATE DATABASE myapp;
```

Update credentials in `backend/src/main/resources/application.properties` if needed:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/myapp
spring.datasource.username=postgres
spring.datasource.password=postgres
```

Hibernate will create/update the schema automatically on first run (`ddl-auto=update`).

---

## 2 — Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

### API endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/items` | List all items |
| GET | `/api/items/{id}` | Get item by id |
| POST | `/api/items` | Create item |
| PUT | `/api/items/{id}` | Update item |
| DELETE | `/api/items/{id}` | Delete item |

---

## 3 — Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The app will be available at `http://localhost:5173`.

Vite proxies all `/api/*` requests to `http://localhost:8080`, so no CORS issues during development.

---

## Project structure

```
myapp/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/app/
│       │   ├── AppApplication.java
│       │   ├── controller/ItemController.java
│       │   ├── model/Item.java
│       │   ├── repository/ItemRepository.java
│       │   └── service/ItemService.java
│       └── resources/
│           └── application.properties
└── frontend/
    ├── index.html
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── index.css
        ├── api/client.ts       ← typed API wrapper
        └── components/ItemList.tsx
```

---

## Building for production

**Backend JAR:**
```bash
cd backend && mvn package
java -jar target/app-0.0.1-SNAPSHOT.jar
```

**Frontend static files:**
```bash
cd frontend && npm run build
# output in frontend/dist/
```
