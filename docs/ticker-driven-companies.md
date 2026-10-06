# Ticker-driven company research

## Request flow

`/company/{TICKER}` → Next.js server fetch → Spring controller → CompanyService / FinancialDataService → PostgreSQL or SecDataService → existing SecFinancialDataParser → existing FinancialMetricNormalizer.

Tickers are trimmed, uppercased, and validated at the backend boundary. CompanyService first checks PostgreSQL; missing companies resolve through the SEC-maintained [company ticker directory](https://www.sec.gov/files/company_tickers.json). CIKs are padded to ten digits. The directory is cached in memory for 24 hours, with serialized refreshes and a short stale-cache fallback for known tickers during outages. No paid metadata service is required.

All SEC HTTP calls remain in SecDataService. Set `SEC_USER_AGENT` to `EquityLens your-real-contact-address` for deployment. Requests have connection/read timeouts and are serialized with at least 150 ms between starts per application process. SEC integration endpoints can be overridden with `sec.tickers-url` and `sec.facts-base-url` for tests. See [SEC API documentation](https://www.sec.gov/search-filings/edgar-application-programming-interfaces).

## Persistence and concurrency

- PostgreSQL `INSERT ... ON CONFLICT DO NOTHING` plus a case-insensitive unique ticker index prevents duplicate company records under concurrent requests.
- Each ticker has its own company identity. CIK is an issuer identifier and is **not unique**: multiple share classes can map to the same SEC issuer.
- Financial reads and refreshes lock the company row in a transaction. Concurrent requests for a new company's statements wait for one import, then reuse the committed data.
- The existing parser is the sole import pipeline. A successful import replaces only the requested company's metrics atomically. Fetch/parse failures leave its previous metrics intact.
- `companies.financial_import_count` records the number of rows in a completed import. Empty data, a different row count, or a legacy import lacking one of revenue/net income/cash/assets triggers repair through that same importer.
- Legacy datasets containing all four core metrics are adopted without a fresh SEC download. A successful SEC import can legitimately omit some metrics; that does not trigger repeated imports.
- Completeness checks detect missing/extra rows, not arbitrary value corruption or every historical gap in a legacy dataset. Use the explicit refresh endpoint to replace such data or fetch newer filings. There is no scheduled refresh in this feature.

`db/ticker-schema.sql` runs idempotently after Hibernate's existing schema update: it removes the old single-column CIK uniqueness constraint, adds the case-insensitive ticker index, and adds the import-count column. Existing company IDs and financial rows remain intact. No new dependency or migration framework was added. Existing case-variant duplicate tickers, if present in a different database, must be reconciled before creating the unique index; the migration does not silently delete data.

## API contracts

- `GET /api/companies/{ticker}` — resolve/create company metadata, without importing facts yet. Returns ticker, cik, name, sector, industry, id through a DTO.
- `GET /api/companies/{ticker}/financials` — return stored selected SEC observations or import/repair on demand. Existing fields remain: company, id, metric, periodStart, periodEnd, value, unit, filingDate, form, frame.
- Existing `/financials/normalized`, `/annual`, `/quarterly`, `/income-statement`, `/balance-sheet`, `/cash-flow`, and `/ttm` endpoints now share the same on-demand loader.
- `POST /api/companies/{ticker}/financials/import` — explicit atomic refresh; repeated imports replace observations instead of appending duplicates. Database row IDs may change on refresh.
- Company summary, market-data, and valuation services also use the central ticker lookup. Market quotes still require the existing market-data provider and are outside SEC financial imports.
- `POST /api/companies` is retained for sector/industry metadata; company identity is now resolved via SEC rather than accepted from caller-supplied CIK/name fields.
- Invalid syntax: 400. Unknown ticker or unavailable SEC company facts: 404. Unsupported/empty parsed financial dataset: 422. SEC failure: 502/503. Errors return problem details.

The financial normalizer preserves rolling twelve-month flows from quarterly filings as `ROLLING_YEAR` when a confirmed fiscal-year boundary proves they are not fiscal annual observations. These do not enter annual statement views, fiscal-quarter anchors, or four-quarter TTM sums. The existing TTM arithmetic is unchanged.

## Frontend

- `/company/META`, `/company/AAPL`, `/company/AMZN`, etc. render the same dashboard using only the route ticker.
- `/` is a search landing page. Existing `/?ticker=aapl` links redirect to `/company/AAPL`; lowercase company paths canonicalize similarly.
- Search trims and validates input, then navigates to the company route. Route-level loading replaces the previous dashboard during data fetching; the dashboard state is keyed by ticker.
- Unknown-company pages, invalid-input messages, import loading text, and retryable error boundaries are provided.
- Server requests allow up to 120 seconds for a first import and use no-store. The browser never calls SEC directly.
- SEC's ticker directory does not supply sector/industry. Existing metadata is preserved, and newly discovered companies display those fields as unavailable.

## Run and verify

From the repository root, with the existing PostgreSQL container running:

```bash
cd backend
./gradlew test build
./gradlew bootRun
```

In another terminal:

```bash
cd frontend
npm run lint
npm test
npm run build -- --webpack
npm run start
```

The frontend defaults to backend port 8080. Override `BACKEND_URL` in `frontend/.env.local` if needed. `FINNHUB_API_KEY` is optional for SEC-only financial research.

From the repository root:

```bash
python3 backend/scripts/verify-tickers.py
# Also refresh AAPL using real SEC data and compare persisted observations:
python3 backend/scripts/verify-tickers.py --refresh
# For a backend started on a different port:
python3 backend/scripts/verify-tickers.py --base-url http://localhost:8081
```

The script verifies real META/AAPL/AMZN identities and distinct financials, ownership, repeated/concurrent reads, provenance, TTM, and 400/404 responses. It does not use fixtures. Unit/integration tests replace SEC HTTP with controlled responses; PostgreSQL integration-test writes roll back.

Current parser coverage remains the existing US-GAAP 10-K/10-Q (and amendments) tags. IFRS-only issuers, unsupported forms, and issuer-specific custom tags may lack supported observations. Existing USD-only dashboard presentation is unchanged.
