> Historical subsystem verification. See [current project checks](verification.md) for the latest repository validation.

# MCP / AI implementation verification — 2026-10-01

## Results

- Backend: `./gradlew test build` passed. The 104-test suite includes the original REST/dashboard/normalization/market tests and new tool, transport, provider and assistant tests.
- Frontend: `npm test` passed, 35 tests across eight files. `npm run lint` and `npm exec tsc -- --noEmit` passed.
- Production: `npm run build -- --webpack` passed, including Next.js TypeScript checks. Default Turbopack build failed on denied worker port binding,  the existing documented webpack fallback succeeded.
- Real official-SDK live check: `./gradlew mcpSmokeTest -PmcpUrl=http://127.0.0.1:8086` passed, ten tools discovered, MCP revision 2025-11-25 negotiated.
- META/AAPL/AMZN/NVDA: identity, financials, revenue history, growth, profitability, market snapshot, daily/monthly/yearly candles, unsupported hourly and numerical REST/MCP revenue parity passed. All four market snapshots reported OK. Search and four-company comparison passed.
- Valuation: META had available legacy stored valuation data; AAPL/AMZN/NVDA correctly returned DATA_UNAVAILABLE. No quote refresh/new calculation was fabricated.
- Chromium: all 12 Overview/Market/Assistant company pages returned HTTP 200. Company search/switching and company-specific assistant navigation passed; 390px mobile layout had no horizontal overflow; no browser JavaScript errors. Desktop and mobile layouts were inspected; public examples are in `docs/images/`.
- Real browser chat: browser → Next.js proxy → local Ollama model → SDK MCP client/server → existing company service → evidence-backed answer passed for META. The retrieved company profile appeared in the UI and clear/reset passed. This check used the final rebuilt frontend and backend.
- Local model: Ollama was running with `qwen2.5-coder:7b`. A real assistant request selected `get_company`, executed it through the SDK MCP client, and returned Apple Inc.'s CIK 0000320193 with the successful tool result in response evidence.
- Local model limitation: a more complex financial-history request produced invalid period parameters. It returned an error tool result and the backend suppressed the unsupported answer. That installed coding model is not reliably correct for every financial research prompt. Native tool calls and its exact JSON-envelope compatibility path are covered by tests. Use/configure an appropriate tool-capable model and inspect retrieved evidence.
- Client bundle scan: no `AI_ACCESS_TOKEN`, `STASHGAMMA_API_KEY`, Ollama port/address or StashGamma/Finnhub provider URL markers appeared in built browser JavaScript.
- Diff review: no new SQL or repository access in the MCP/AI layer; financial adapters call existing application services, the market provider abstraction and REST controllers remain intact, and no financial formulas or database copies were added. No provider keys were included in project files. Whitespace checks passed.

A production Next.js proxy regression was fixed and tested: origin validation now uses the actual request Host rather than the internal Next.js listening hostname, while still rejecting cross-origin requests.

The existing market integration fixture was adjusted to use completed calendar months: on October 1 its relative dates collapsed the expected three months into two. Only test data changed, not production market behavior. The live smoke comparison uses numeric comparison rather than JSON numeric node equality so NVDA decimal representation does not cause a false mismatch.
