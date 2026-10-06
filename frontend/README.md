# EquityLens frontend

The Next.js App Router frontend presents company financials, historical market data, and a dedicated research assistant. It uses React, TypeScript, Tailwind CSS, and Lightweight Charts.

## Routes and data access

- `/company/[ticker]`: financial overview and company navigation.
- `/company/[ticker]/market`: historical prices and optional live-market analytics.
- `/company/[ticker]/assistant`: research with explicit company context.
- `/assistant`: independent research page.
- `/api/assistant` and market proxy routes: server-side backend access.

Overview and Market use the existing REST backend. Research uses the backend assistant, which retrieves data through MCP. The optional live-market panel handles unavailable data separately from historical charts.

## Development

```bash
cp .env.example .env.local
npm ci
npm run dev
```

`BACKEND_URL` defaults to `http://localhost:8080`. Keep backend/provider addresses and credentials server-side; use no `NEXT_PUBLIC_` secrets. Configure `AI_ACCESS_TOKEN` identically to the backend if enabled.

```bash
npm run lint
npm test
npm exec tsc -- --noEmit
npm run build
```

See [local development](../docs/development.md), [verification](../docs/verification.md), and [third-party notices](THIRD_PARTY_NOTICES.md).
