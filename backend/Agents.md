# EquityLens

## Project
Free financial research platform.

## Backend
- Spring Boot 4.1.1
- Java 21
- Gradle Groovy
- PostgreSQL 17
- SEC EDGAR as primary financial data source

## Frontend
- Next.js
- React1

## Rules
- Do not use Maven.
- Do not introduce unnecessary dependencies.
- Prefer production-quality implementations.
- Run Gradle tests/build after backend changes.
- Do not modify unrelated files.
- Preserve existing APIs unless the task requires changing them.
- Use DTOs instead of exposing JPA entities from new APIs.
- SEC data must be normalized before being exposed to the frontend.

## Development
PostgreSQL runs in Docker Desktop. Start its existing container before backend tests or bootRun; use the datasource settings in application.properties.

Backend:
./gradlew test
./gradlew bootRun

Frontend:
npm install
npm run dev