# Architecture

## Financial and historical market data

Spring Boot exposes REST controllers backed by application services, JPA repositories, and PostgreSQL. Company resolution uses ticker/CIK identity. Financial ingestion reads SEC Company Facts; normalization selects usable periods and units before dashboard calculations consume them. Existing services retain financial growth, margin, cash-flow, and valuation behavior.

Historical market access uses `MarketHistoryService` and `MarketDataProvider`. Provider-specific requests and capability checks stay behind this interface. Cached prices and import state share the existing database. Daily data supports monthly/yearly aggregation; hourly data is not fabricated. Provider failures and absent data have explicit response states.

Next.js uses server-side backend calls and server proxies for browser interactions. Company Overview and Market pages retain REST access.

## Research interface

The official MCP Java SDK provides an additive servlet endpoint in the backend. Tool adapters validate bounded inputs, call existing services, and return compact structured results. `AIProvider` separates local model integration from research orchestration. The Ollama implementation selects tools through the MCP client; it has no SQL or filesystem capability.

Company context is explicitly submitted with chat requests. Tool evidence retains available reporting dates, filing observations, and provider metadata. Exact derived-metric lineage is limited by the existing domain model and is not invented. See [assistant design](ai-assistant.md).

## Optional streaming

Provider WebSocket adapters publish canonical events to Kafka. Spark Structured Streaming validates trade/reference events, performs event-time aggregation, and persists derived bars and analytics to PostgreSQL. Kafka analytics notifications trigger bounded REST/SSE updates to the frontend.

The development overlay uses one Kafka broker and Spark local execution. It demonstrates processing and recovery mechanics rather than a production distributed deployment. Checkpoints and idempotent database writes support replay; database writes and Kafka notifications are not one atomic transaction. See [streaming design](streaming.md) for implemented scope and limits.

## Boundaries

One normalized data store serves dashboard and research. Provider credentials remain server-side. Optional components are independently configurable; unavailable market feeds, models, or streaming infrastructure must not become dependencies of ordinary SEC dashboard requests. Deployment authentication, rate limiting, monitoring, and infrastructure hardening remain necessary before public service exposure.
