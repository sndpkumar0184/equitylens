# Ticker dashboard regression: diagnosis and verification

## Root cause

The reproduced failure was on `http://localhost:3007/company/AMZN`.
That Next.js process had `BACKEND_URL=http://127.0.0.1:8087`, but no backend
was listening on 8087. Its streamed HTML returned HTTP 200 while displaying
the company-data-unavailable error boundary. Backends on 8086 and 8088 already
returned complete dashboards for META, AAPL, AMZN and NVDA.

The previous verification backend ran directly from `backend/build/libs`.
Rebuilding that executable JAR while it was running produced
`NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy` in its log.
It was subsequently stopped, leaving its frontend running against the dead port.
This was a local process/artifact lifecycle regression introduced during
streaming verification, rather than a Kafka financial-ingestion race.

History and the working diff confirm that SEC financial ingestion has not been
moved to Kafka. Company/CIK lookup, SEC Company Facts parsing, transaction-bound
FinancialMetric persistence and dashboard queries remain synchronous. Cached
complete imports are read without a SEC request. Kafka carries only the added
canonical market stream; Spark persists its derived analytics separately.

## Fix

`scripts/run-backend.sh` builds and copies the executable JAR to a unique runtime
directory before starting Java. Later builds cannot overwrite that running copy.
The stopped 8087 backend was restored using this launcher; its Kafka derived-event
consumer remains enabled and connected. Existing frontend code was unchanged.

An independently reproduced legacy `/market-data` issue returned HTTP 500 for a
company without a stored quote. It now returns HTTP 404, without fetching,
fabricating or substituting prices. The current Market UI uses `/market` and the
existing StashGamma provider, which continues to return actual historical prices.

Derived-consumer notification failures now propagate to its retry boundary
before offset acknowledgement. Malformed/unsupported messages remain explicitly
logged and skipped. This consumer does not persist SEC financial data: Spark is
the durable market analytics writer, and SEC persistence remains synchronous.

## Verification

- Full backend `./gradlew test --rerun-tasks build`: 124 tests passed.
- Four parameterized dashboard cases run with the live Kafka consumer enabled
  and its broker deliberately unreachable (`127.0.0.1:1`). Persisted financial
  data remains readable; SEC is not called for complete cached imports.
- Invalid ticker still returns 404. Initial ingestion/mapping upgrade/duplicate
  import tests remain in the existing financial integration suite.
- Consumer tests cover valid/duplicate events, malformed/version/symbol failures,
  notification failure propagation and deterministic retry without sleeps.
- Missing legacy quotes return 404 without contacting a provider or writing data.
- Frontend lint and all 38 tests pass; production webpack build passes.
  The sandboxed build initially failed launching its TypeScript subprocess;
  the unrestricted build succeeded.
- Actual company and dashboard APIs on restored port 8087 return 200 for all
  four tickers, with eight annual and eight quarterly periods and non-null
  revenues. Invalid `NOTREAL123` returns 404.
- Actual annual revenue values: META 200,966,000,000; AAPL 416,161,000,000;
  AMZN 716,924,000,000; NVDA 215,938,000,000 (USD, latest reported fiscal years).
- Browser verification covers Overview KPI cards/charts and Market routes.
- All eight browser routes render without JavaScript errors; each historical
  Market API returns 251 actual StashGamma daily observations.
- Spark deterministic analytics: four tests pass. Real PostgreSQL analytics
  persistence/retry-idempotency: one test passes.

Persisted SEC imports inspected before changes:

| Ticker | CIK | FinancialMetric rows | Import mapping version |
| --- | --- | ---: | ---: |
| AMZN | 0001018724 | 1448 | 2 |
| AAPL | 0000320193 | 1191 | 2 |
| META | 0001326801 | 996 | 2 |
| NVDA | 0001045810 | 1313 | 2 |

For each, the import count matched the stored rows. Company lookup and dashboard
queries read these existing records; there is no SEC Kafka message, topic or
financial consumer in this repository's current path. Nothing disappeared from
PostgreSQL. Kafka broker topics had four healthy partitions and one-day retention;
the restored backend successfully joined its derived-analytics consumer group.

## Run reliably

From the repository root, load your existing server-side environment configuration
and start infrastructure using the documented development workflow. Then:

```bash
docker compose -f docker-compose.yml -f docker-compose.realtime.yml up -d
bash scripts/run-backend.sh --server.port=8080
```

In a separate terminal:

```bash
cd frontend
BACKEND_URL=http://127.0.0.1:8080 npm run dev -- --port 3000
```

For a production frontend, run `npm run build -- --webpack` followed by
`BACKEND_URL=http://127.0.0.1:8080 npm run start -- --port 3000`.
The frontend's backend URL must match the actual running backend port. Keep
provider credentials server-side. Realtime provider/Spark configuration remains
documented in `realtime-market-intelligence.md`; this fix does not disable Kafka
or change any provider settings. Synthetic replay is restricted to explicitly
selected development analytics and is never used as SEC dashboard data.

No financial schema changes, financial calculations, MCP/AI changes or historical
data were reverted. No commits or pushes were made. Other stale local frontend
instances pointing to stopped ports are independent processes, not repaired
through a hidden fallback to a different backend.
