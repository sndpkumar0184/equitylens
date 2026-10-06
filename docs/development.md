# Local development

## Prerequisites

Use Java 21, a Node.js version supported by the checked-in Next.js release, npm, and Docker Compose. PostgreSQL 17 is supplied by Compose. Optional research uses Ollama; optional streaming uses the Kafka/Spark overlay.

## Environment and database

From the repository root:

```bash
cp .env.example .env
# Edit .env: choose a local database password and a real SEC contact identity.
docker compose up -d postgres
set -a
source .env
set +a
```

The root example is shell-compatible. Compose automatically loads `.env`, but Java does not: export the environment in the terminal that starts the backend. Database defaults in configuration are for isolated local development only. For an existing PostgreSQL volume, use its existing credentials: changing Compose environment variables does not change a database user's password. Do not delete a populated volume to resolve a configuration mismatch.

`SPRING_DATASOURCE_URL` controls the backend connection; `POSTGRES_USER` and `POSTGRES_PASSWORD` configure both the backend and new Compose database initialization. If changing the database name, update the JDBC URL too. Keep all real environment files ignored.

## Backend

```bash
cd backend
./gradlew bootRun
```

The default port is 8080. Schema initialization uses the existing Hibernate setup and checked-in idempotent SQL scripts. Set `SEC_USER_AGENT` to a contact identity for SEC requests. `STASHGAMMA_API_KEY` enables the historical Market integration; absent credentials produce an unavailable state without disabling financial dashboards. `FINNHUB_API_KEY` supports the existing quote integration where used.

## Frontend

In a separate terminal, from the root:

```bash
cd frontend
cp .env.example .env.local
npm ci
npm run dev
```

The default URL is http://localhost:3000. `BACKEND_URL` is read by the Next.js server. If `AI_ACCESS_TOKEN` is configured in the backend, set the same value in `.env.local`. Never place credentials in `NEXT_PUBLIC_` variables.

## Optional research

Install Ollama and a tool-capable model, then start its runtime:

```bash
ollama pull qwen2.5:7b
ollama serve
```

If an Ollama service already runs, do not start a second instance. Configure `AI_LOCAL_MODEL` for an installed model. If changing the backend port, update `AI_MCP_URL` and frontend `BACKEND_URL`. The assistant reports runtime/model failures rather than simulating answers. See [MCP tools and research](ai-assistant.md) for access controls and independent testing.

## Optional streaming

Follow [streaming setup](streaming.md). Both backend streaming flags default to false. Normal dashboard requests work independently of Kafka, Spark, and model availability.

## Checks

Run backend `./gradlew test build`; in `frontend`, run `npm run lint`, `npm test`, `npm exec tsc -- --noEmit`, and `npm run build`. In environments where Turbopack cannot create worker ports, `npm run build -- --webpack` verifies a production build with the supported alternate bundler. See [verification](verification.md) for actual results.
