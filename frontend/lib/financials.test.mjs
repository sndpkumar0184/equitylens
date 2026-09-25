import assert from "node:assert/strict";
import { test } from "vitest";
import { balanceAt, money, selectPeriods } from "./financials.ts";

const row = (period, periodEnd, unit = "USD") => ({
  period, periodStart: "2025-01-01", periodEnd, unit,
  revenue: 100, netIncome: -10, costOfRevenue: null, grossProfit: null, operatingIncome: null,
});

test("duration selection excludes YTD, unknown periods, and other currencies and sorts oldest first", () => {
  const data = [row("FY", "2025-12-31"), row("YTD", "2025-06-30"), row("Q1", "2025-03-31"), row("QUARTER", "2025-06-30"), row("FY", "2024-12-31"), row("FY", "2023-12-31", "EUR"), row("UNKNOWN", "2025-09-30")];
  assert.deepEqual(selectPeriods(data, "annual").map(x => x.periodEnd), ["2024-12-31", "2025-12-31"]);
  assert.deepEqual(selectPeriods(data, "quarterly").map(x => x.period), ["Q1", "QUARTER"]);
  assert.equal(data[0].periodEnd, "2025-12-31");
});

test("balances match reporting date and currency without silently using a newer balance", () => {
  const balances = [
    { period: "POINT_IN_TIME", periodEnd: "2026-03-31", unit: "USD", cash: 50, assets: 100 },
    { period: "POINT_IN_TIME", periodEnd: "2025-12-31", unit: "EUR", cash: 99, assets: 999 },
    { period: "POINT_IN_TIME", periodEnd: "2025-12-31", unit: "USD", cash: 0, assets: 80 },
  ];
  assert.equal(balanceAt(balances, "2025-12-31")?.cash, 0);
  assert.equal(balanceAt(balances, "2024-12-31"), undefined);
  assert.equal(balanceAt(balances, undefined), undefined);
});

test("missing values stay distinct from zero and losses keep their sign", () => {
  assert.equal(money(null), "—");
  assert.equal(money(undefined), "—");
  assert.equal(money(NaN), "—");
  assert.equal(money(0), "$0");
  assert.equal(money(-1000000), "-$1M");
  assert.equal(money(1234567, false), "$1,234,567");
});
