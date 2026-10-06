# EquityLens MCP and local AI assistant

EquityLens continues to serve the dashboard through its original REST controllers. The new `/mcp` endpoint is an additional agent interface in the same Spring Boot process. No second database, SEC import pipeline, price pipeline, or financial calculation engine was added.

## Architecture

The repository uses Spring Boot 4.1.1, Java 21, Gradle, Spring MVC, JPA and PostgreSQL 17. Existing `CompanyService`, `FinancialDataService`/`FinancialMetricNormalizer`, `DashboardService`/`DashboardCalculations`, `MarketHistoryService`, `MarketDataProvider`, and `ValuationService` remain the source of identity, normalized SEC data, calculated metrics, provider-neutral prices and valuation. Tables remain `companies`, `financial_metrics`, `market_data`, `market_prices`, and `market_price_imports`. Existing Hibernate/schema initialization is unchanged.

The frontend uses Next.js 16.3.2 App Router, React 19, Tailwind, server-side backend calls and browser-to-Next.js market proxies. Overview, Market, charts and company search retain their existing REST paths. The new company tab is `/company/{ticker}/assistant`; `/assistant` provides an independent research page.

The flow is browser → Next.js `/api/assistant` proxy → backend `/api/assistant/chat` → `AIProvider` → discovered MCP tools → official Java SDK HTTP client → backend MCP servlet → existing services → existing PostgreSQL. The model receives no database credentials, SQL capability, filesystem tools or provider URL.

The server and client use the official `io.modelcontextprotocol.sdk` Java SDK **2.0.1**, with `mcp-core` and `mcp-json-jackson3`. Its Servlet transport is compatible with Boot 4/Jakarta Servlet and avoids introducing a mismatched Spring AI starter or downgrading Spring. It negotiates through MCP **2025-11-25**, the revision supported by this stable SDK; it does not claim support for the newer 2026-07-28 revision. See [official SDK releases](https://github.com/modelcontextprotocol/java-sdk/releases) and [server documentation](https://java.sdk.modelcontextprotocol.io/latest/server/).

No MCP resources were added: company/profile/summary tools already provide the useful read-only views. Resources can be introduced later for a client that needs URI-based access.

## Run locally

From a repository checkout, start PostgreSQL and load the backend environment as described in the [development guide](development.md). Keep the existing SEC and market configuration. Start Ollama if necessary (`ollama serve`) and install a tool-capable model:

```bash
ollama pull qwen2.5:7b
ollama list
cd backend
AI_PROVIDER=local AI_LOCAL_MODEL=qwen2.5:7b ./gradlew bootRun
```

Then start Next.js:

```bash
cd frontend
BACKEND_URL=http://localhost:8080 npm run dev
```

Open http://localhost:3000/company/AAPL/assistant or http://localhost:3000/assistant. Select another company with the existing search. The ticker is explicitly included in the backend request; explicit tickers in questions override active context. Company route changes remount the assistant and clear the prior conversation. Reset also aborts pending requests and ignores late responses.

The integration has also been exercised with `qwen2.5-coder:7b`. Set `AI_LOCAL_MODEL` to a tool-capable model installed in your runtime. Model quality and reliable tool selection vary. The native Ollama `/api/chat` API is used. For local templates that emit an exact `{"name":"tool_name","arguments":{...}}` JSON envelope as content, the provider accepts that envelope only when its name matches a discovered tool. It never extracts tool commands from prose. See [Ollama tool calling](https://github.com/ollama/ollama/blob/main/docs/capabilities/tool-calling.mdx).

If the runtime/model is absent, the UI displays an actionable error. If the model does not retrieve data, the backend suppresses its unverified answer. There is no simulated analysis or cloud API fallback.

## Configuration

All settings are environment variables; no source changes are needed.

| Variable | Default | Purpose |
| --- | --- | --- |
| `AI_ENABLED` | `true` | Disable assistant without disabling REST |
| `AI_PROVIDER` | `local` | Select the provider implementation; unknown values leave assistant unavailable |
| `AI_LOCAL_URL` | `http://127.0.0.1:11434` | Backend-only Ollama runtime address |
| `AI_LOCAL_MODEL` | `qwen2.5:7b` | Installed tool-capable model |
| `AI_MCP_URL` | `http://127.0.0.1:8080` | Local backend origin; update when changing backend port |
| `MCP_ENABLED` | `true` | Enable additive MCP servlet |
| `MCP_ALLOWED_HOSTS` | `localhost:*,127.0.0.1:*` | Comma-separated allowed MCP Host headers |
| `MCP_ALLOWED_ORIGINS` | `http://localhost:3000,http://127.0.0.1:3000` | Comma-separated MCP browser origins |
| `AI_ACCESS_TOKEN` | empty | Server-only bearer token for MCP and assistant endpoints; set identically in backend and Next.js server |
| `BACKEND_URL` | `http://localhost:8080` | Next.js server-side backend address |
| `STASHGAMMA_API_KEY` | existing setting | Optional existing market provider credentials |
| `MARKET_DATA_PROVIDER` | `stashgamma` | Existing provider abstraction |
| `SEC_USER_AGENT` | existing setting | Contact identity for existing SEC importer |

Do not prefix AI/provider credentials with `NEXT_PUBLIC_`. No paid cloud key is required. Implement another `AIProvider` bean with a conditional provider setting to add a future local/cloud implementation; the UI, research orchestration and MCP tools do not need rewriting.

Default MCP/assistant access is loopback only. A configured `AI_ACCESS_TOKEN` is required on every MCP/assistant request, including loopback; Next.js adds it on the server. Non-loopback direct access requires a token. For remote MCP clients, configure explicit Host/Origin allowlists and use an authenticated TLS deployment. A reverse proxy must enforce access at the edge: a proxy connecting over loopback is not a user authentication boundary. The public Next.js assistant proxy needs the deployment's normal authentication/rate limiting if exposed beyond local development.

## Tools and inputs

All ten tools provide structured content plus equivalent JSON text for compatibility. Unknown parameters are rejected. Missing fields stay null; errors are sanitized and never echo provider exceptions.

| Tool | Required inputs | Optional inputs / behavior |
| --- | --- | --- |
| `search_companies` | `query` | Ticker/name, 1–80 characters, max 20 registered company DTOs; does not search all SEC issuers |
| `get_company` | `ticker` | Ticker, CIK, name, available sector/industry |
| `get_financials` | `ticker` | `type`, `period`, `start`, `end`, `limit`; normalized USD rows |
| `get_financial_history` | `ticker`, `metric` | Same period options; chronological values |
| `get_market_snapshot` | `ticker` | Latest provider close/date, changes, volume, 52-week range, returns, status/source; not a live quote |
| `get_price_history` | `ticker`, `timeframe` | `start`, `end`; daily/monthly/yearly via existing market service; hourly explicitly unsupported |
| `get_growth_metrics` | `ticker` | Same period options; existing revenue growth; earnings/FCF growth unavailable |
| `get_profitability_metrics` | `ticker` | Same period options; existing gross/operating/net margins |
| `compare_companies` | `tickers` | 2–5 unique tickers, optional `metrics`; latest annual rows with fiscal dates and per-company status |
| `get_valuation_metrics` | `ticker` | Existing TTM valuation only; existing quote data required, no forced refresh |

`type`: `annual` (default) or `quarterly`. `limit`: default 5, max 10 annual / 40 quarterly, within existing bounded ten-year observations. Dates are inclusive ISO `YYYY-MM-DD`, filtering reporting period end. `period`: `FY2025`, `Q12025`, `QUARTER2025`, or `2025`; years refer to the period end year because a separate fiscal-year label is not stored in dashboard DTOs. Quarterly histories inherit the existing normalization/derivation rules.

Metrics: `revenue`, `netIncome`, `grossProfit`, `operatingIncome`, `revenueGrowth`, `grossMargin`, `operatingMargin`, `netMargin`, `freeCashFlow`, `operatingCashFlow`, `capitalExpenditures`, `cash`, `debt`, `assets`, `liabilities`. Growth/margins use percentage points. Comparison preserves different companies' fiscal reporting dates; it does not pretend different fiscal years are calendar-aligned.

Financial results retain ticker, period dates, currency, calculation source, and bounded filing source observations (metric, original period dates, filing date, form, frame, unit). These observations include preceding calculation context and are explicitly **not exact one-to-one provenance** of every derived quarter/ratio. No SEC accession identifiers/URLs exist in these entities, so none are invented. Price results retain provider identity, freshness/status, timestamps and price basis. Valuation output is the existing service calculation and period; quote timestamps are not present in its existing DTO.

Read-only describes the exposed user capabilities. Existing service reads can register an SEC-resolved company and lazily populate/upgrade financial/market caches. No MCP tool exposes import, forced refresh, entity edits, raw SQL, filesystem operations, trading or portfolio transactions.

## Connect/debug independently

Connect any Streamable HTTP MCP client to `http://localhost:8080/mcp`. If configured, supply `Authorization: Bearer <AI_ACCESS_TOKEN>` from a private environment/secret store. For a graphical inspector:

```bash
npm exec --package=@modelcontextprotocol/inspector -- mcp-inspector
```

Choose Streamable HTTP, enter the MCP URL, connect, list tools, and call them. Example tool names and argument objects:

```json
{"name":"get_financial_history","arguments":{"ticker":"NVDA","metric":"revenue","type":"annual","limit":5}}
{"name":"get_price_history","arguments":{"ticker":"AAPL","timeframe":"monthly","start":"2025-01-01","end":"2025-12-31"}}
{"name":"compare_companies","arguments":{"tickers":["AAPL","MSFT","AMZN"],"metrics":["revenue","revenueGrowth","operatingMargin","freeCashFlow"]}}
```

A repeatable live check uses the official Java SDK, separately from deterministic tests:

```bash
cd backend
./gradlew mcpSmokeTest -PmcpUrl=http://127.0.0.1:8080
```

It negotiates MCP, discovers ten tools, checks META/AAPL/AMZN/NVDA, compares financials against REST, verifies daily/monthly/yearly and unsupported hourly prices, tests search/comparison and reports missing valuation data without inventing it.

```bash
cd backend
./gradlew test build
cd ../frontend
npm run lint
npm test
npm exec tsc -- --noEmit
npm run build
# If restricted worker ports prevent Turbopack:
npm run build -- --webpack
```

## Controls and limitations

Tickers use the existing validator. Schemas and adapters reject invalid types, dates, metrics, duplicate/oversized comparisons and unknown fields. Queries remain parameterized through existing repositories. MCP payloads are capped at 64 KiB; price responses at 366 candles (latest subset, marked truncated); filing context at 100 observations (marked truncated). Conversations accept 20 messages, 4,000 characters each, 24,000 total; only user/assistant roles are accepted. Research uses up to 12 calls and six model rounds with a four-minute deadline checked between rounds, bounded HTTP timeouts and two concurrent analyses. Model inputs/results are bounded and runtime responses are capped at 128 KiB. Requests are stateless; browser history is passed explicitly rather than copied into another database.

The model must distinguish facts, calculated metrics, interpretation and uncertainty, cite only actual tool data, and disclose missing/stale/truncated data. Evidence is independently returned and expandable in the UI. Prompt instructions and evidence improve traceability but do not guarantee model correctness. Review retrieved data for material decisions.

Current limitations: hourly candles are unavailable; earnings/FCF growth are not calculated; search covers registered companies; valuation depends on legacy stored quote data and may be missing; exact derived-metric filing lineage is not stored; history is bounded; no streaming progress events (loading state is live, completed retrieval activity is shown with the answer); local model reliability/speed depends on model/runtime/hardware. The official stable SDK used here does not yet implement the newer 2026-07-28 protocol revision.
