# NextCart

NextCart is a single-vendor electronics e-commerce platform built with Next.js, Java Spring Boot, and PostgreSQL.

## Current setup

The initial project structure is now ready:

```text
nextcart/
├── frontend/   Next.js frontend application
└── backend/    Spring Boot REST API application
```

This phase contains only the application foundations. Database integration, authentication, product management, cart, checkout, orders, payments, and admin features will be added in later phases.

## Frontend

Location: `frontend/`

Created with:

- Next.js `16.3.8`
- React `19.2.8`
- TypeScript
- ESLint
- App Router
- `src/` directory structure
- npm package manager

Run the frontend in development mode:

```powershell
cd frontend
npm run dev
```

The development server runs at `http://localhost:3000` by default.

Create a production build:

```powershell
cd frontend
npm run build
```

## Backend

Location: `backend/`

Created with:

- Spring Boot `4.0.0`
- Java `21` target configuration
- Maven
- Spring Web MVC
- Spring Boot Actuator
- Spring Boot DevTools
- Package: `com.nextcart.backend`
- Artifact: `nextcart-backend`
- PostgreSQL with Flyway database migrations

### PostgreSQL setup

Create a local PostgreSQL database named `nextCart` in pgAdmin. The backend reads
the connection settings from environment variables:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/nextCart"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="<your-local-password>"
```

`DB_URL` and `DB_USERNAME` have local defaults in
`backend/src/main/resources/application.properties`; the password is intentionally
not stored in the repository.

Flyway applies migrations from
`backend/src/main/resources/db/migration` when the backend starts. The initial
`V1__create_products_table.sql` migration creates the products table.

Authentication endpoints are available under `/api/auth`:

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/forgot-password
POST /api/auth/reset-password
```

The frontend authentication pages are available at `/login`, `/register`,
`/forgot-password`, and `/reset-password`. Password reset email delivery is not
configured yet; the backend creates a short-lived reset token for the future
email integration.

Run the backend with Maven:

```powershell
cd backend
mvn spring-boot:run
```

Run backend tests:

```powershell
cd backend
mvn test
```

The backend currently starts with the generated Spring Boot application class. No database or business API has been added yet.

## Verified setup

- `frontend`: `npm run build` passed successfully.
- `backend`: `mvn -q test` passed successfully.
- Local tools detected: Node.js `22.19.0`, npm `10.9.3`, Java `25.0.1`, Maven `3.9.11`.

## Scope rule

NextCart is a single electronics shop. The project must not introduce seller accounts, vendor management, marketplace commissions, or other multi-vendor concepts.
