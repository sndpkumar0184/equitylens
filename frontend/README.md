# EquityLens frontend

Next.js App Router, React, TypeScript, and Tailwind CSS. The company dashboard defaults to META; use the ticker input or `/?ticker=META` to select a company already stored in the backend.

## Run locally

Start the existing PostgreSQL container and Spring Boot backend (see `backend/Agents.md`), then run from this directory:

```bash
npm install
npm run dev
```

Open http://localhost:3000. Next.js fetches the company profile, normalized income statements, and balance sheets from Spring Boot on the server. No browser CORS configuration is required.

The default backend address is `http://localhost:8080`. To override it, add a server-only setting to `.env.local`:

```dotenv
BACKEND_URL=http://localhost:8080
```

Requests use a 15-second timeout and do not cache financial responses. The dashboard only reads existing data; it does not create companies or import financials.

## Data presentation

- Annual views use FY observations; quarterly views use Q1–Q4 and standalone QUARTER observations. YTD and unknown durations are excluded.
- Cards use the latest selected statement. Cash/assets match its exact end date and USD unit; unavailable matches display a dash.
- Charts and the table show up to eight reporting periods. Dates are reporting boundaries, not inferred fiscal-year labels.
- Charts preserve negative values and gaps for unavailable observations. The table provides exact values.
- This initial dashboard displays USD financials. Missing values are never converted to zero.
- SVG charts require no chart dependency. System fonts keep builds independent of external font downloads.

## Checks

```bash
npm run lint
npm run build
npm test
```

Vitest and React Testing Library cover period selection, missing values, and dashboard interactions. Use `npm run test:watch` during development.

If a restricted environment blocks Turbopack worker ports, use `npm run build -- --webpack` (and `npm run dev -- --webpack`).
