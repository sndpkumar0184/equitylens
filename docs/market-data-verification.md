# Market-data implementation and verification

Verified September 28, 2026. The root README was committed separately as `c62f85e`. Application changes remain unstaged; existing work was preserved.

## Provider

StashGamma's official daily EOD REST API, behind `DailyPriceProvider`. The existing Finnhub quote API is unchanged. No SEC parser/importer, normalizer, TTM, or valuation code was changed in this iteration. The supplied API key is only in a restricted temporary runtime file outside the repository.

## Schema

- Extended `market_prices` with source, open/high/low, adjusted close, and volume. Prices use decimal(19,6); volume uses bigint.
- Unique company + trading date + source index replaces the old company/date constraint; legacy rows retain source `LEGACY`.
- Added `market_price_imports` to persist successful coverage, fetch time, retry time, and status per company/source.
- Added an idempotent initialization script alongside the existing ticker migration. No data reset or deletion.

## API

`GET /api/companies/{ticker}/market?from=YYYY-MM-DD&to=YYYY-MM-DD`

Optional bounds filter chart history only. Summary metrics always describe the latest available observation. Defaults: one year of chart history through today (America/New_York). Date range cannot be inverted, future-dated, or begin more than 15 years ago. Existing `/market-data` and `/market-data/refresh` remain unchanged.

Return formula: `(latest close / comparison close - 1) * 100`, rounded to four decimals. Comparison is the last session on or before the date 1/3/6/12 months before the latest session, no more than seven calendar days earlier. 52-week extremes use daily high/low in `(latest date - 52 weeks, latest date]`, require an anchor at the start, and remain null if relevant fields are missing.

Successful covered reads reuse the database for six hours. Coverage expansion/new dates trigger an import. Provider errors preserve old observations and set a retry window. Global provider quotas are guarded per process, and HTTP 429 Retry-After is honored. Multiple app instances still share provider-side quotas; per-company database locking prevents concurrent duplicate imports.

## Checks

- Backend: `./gradlew test build` — PASS, 75 tests, zero failures (18 new tests).
- Frontend: `npm run lint` — PASS.
- Frontend: `npm test` — PASS, 23 tests (4 new tests).
- Frontend: `npm run build -- --webpack` — PASS.
- Frontend: `npm exec tsc -- --noEmit` — PASS.
- Real PostgreSQL integration tests cover persisted OHLCV, source isolation, database unique index, idempotent corrections, cache reuse, empty/missing credentials, failed-refresh preservation/backoff, historical bounds and HTTP validation.
- Provider tests cover decimals, malformed responses, incorrect ticker, duplicates, invalid OHLC/volume/dates, optional missing fields, missing key, authentication header/path and global HTTP 429 cooldown.
- Analytics tests cover all four returns, negative/zero returns, weekends, holidays, stale/missing anchors, zero denominator, 52-week high/low and insufficient history.
- Frontend tests cover seven cards, source/date, chart, missing data, stale data, ticker replacement and independent API failure handling. Existing tests were preserved.
- Live META/AAPL/AMZN market endpoints and company pages all returned HTTP 200. Each chart has 250 daily observations. Cached repeat reads retained financial values and fetch time (PostgreSQL rounds timestamp precision to microseconds).
- Independent authenticated provider reads verified every displayed price observation and all seven market metrics as of the cached session. The provider subsequently revised September 25 volume for AAPL (29,400,942 → 30,002,507) and AMZN (32,625,750 → 34,263,902); price values were unchanged. These revisions will be picked up by the next cache refresh. Newer sessions also appeared during verification.
- Credential scan: supplied API key is absent from repository files.

Git status after the README commit: 33 modified files, 44 untracked files, no staged changes. These totals include pre-existing work. No reset, clean, or push was performed.

## Live sample

Captured from the real StashGamma imports; latest trading date September 25, 2026. Prices below are cached observations, not live quotes.

| Ticker | Latest close | 52W high | 52W low | 1M | 3M | 6M | 1Y |
|---|---:|---:|---:|---:|---:|---:|---:|
| META | $751.66 | $779.82 | $520.26 | 31.8586% | 38.4604% | 26.3528% | 0.3672% |
| AAPL | $341.07 | $345.34 | $243.42 | 10.0581% | 23.9578% | 35.0131% | 32.7792% |
| AMZN | $249.67 | $287.20 | $196.00 | -4.3630% | 9.9819% | 17.9302% | 14.4488% |

## Limits

- Provider documents 300 requests/hour, 800/day, 4,000/week. Cache/quota protection is not unlimited coverage.
- Adjustment semantics are not documented, so adjusted close remains null and the UI labels returns as based on provider closes with unverified adjustments. These are not dividend-reinvestment total returns; corporate actions can affect comparisons.
- Cached data can lag newly published sessions until the six-hour refresh; the UI displays the actual trading date. No scheduled refresh or streaming feed.
- Empty/error responses show N/A; cached provider failures show a stale warning.
- StashGamma's published terms require permission for redistribution and written permission for commercial use. The integration does not establish a public deployment license.
- Browser interaction was covered by component tests; live UI verification used server-rendered HTML, not an installed browser automation package.

## Local processes

Left running: backend `http://localhost:8082`, frontend `http://localhost:3003`. Existing processes were not stopped.

Equivalent startup commands, after setting STASHGAMMA_API_KEY in the backend environment:

```bash
cd backend
./gradlew bootRun --args='--server.port=8082'
```

```bash
cd frontend
npm run build -- --webpack
BACKEND_URL=http://localhost:8082 npm run start -- --hostname 127.0.0.1 --port 3003
```

## Created files

- `backend/src/main/java/com/equitylens/controller/MarketHistoryController.java`
- `backend/src/main/java/com/equitylens/dto/DailyPrice.java`
- `backend/src/main/java/com/equitylens/dto/MarketHistoryResponse.java`
- `backend/src/main/java/com/equitylens/entity/MarketPriceImport.java`
- `backend/src/main/java/com/equitylens/providers/DailyPriceProvider.java`
- `backend/src/main/java/com/equitylens/providers/StashGammaDailyPriceProvider.java`
- `backend/src/main/java/com/equitylens/repository/MarketPriceImportRepository.java`
- `backend/src/main/java/com/equitylens/service/MarketAnalytics.java`
- `backend/src/main/java/com/equitylens/service/MarketHistoryService.java`
- `backend/src/main/resources/db/market-price-schema.sql`
- `backend/src/test/java/com/equitylens/providers/StashGammaDailyPriceProviderTest.java`
- `backend/src/test/java/com/equitylens/service/MarketAnalyticsTest.java`
- `backend/src/test/java/com/equitylens/service/MarketHistoryIntegrationTest.java`
- `frontend/components/market-section.test.tsx`
- `frontend/components/market-section.tsx`
- `frontend/lib/market.ts`
- `README.md` (committed separately)
- `docs/market-data-verification.md` (this report)

## Modified files this iteration

- `backend/src/main/java/com/equitylens/entity/MarketPrice.java`
- `backend/src/main/java/com/equitylens/repository/MarketPriceRepository.java`
- `backend/src/main/resources/application.properties`
- `frontend/app/company/[ticker]/page.tsx`
- `frontend/lib/api.test.ts`
- `frontend/lib/api.ts`
