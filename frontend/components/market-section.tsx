"use client";

import { useId } from "react";
import type { MarketData } from "@/lib/market";
import { dateLabel, money } from "@/lib/financials";
import { percent } from "@/lib/dashboard";
import { TrendChart } from "./trend-chart";

export function MarketSection({ ticker, data }: { ticker: string; data: MarketData | null }) {
  const id = useId();
  // Never show a previous company's market observations during navigation.
  const market = data?.ticker === ticker ? data : null;
  const cards = [
    ["Stock Price", market?.latestPrice, false], ["52W High", market?.high52Week, false],
    ["52W Low", market?.low52Week, false], ["1M Return", market?.return1Month, true],
    ["3M Return", market?.return3Month, true], ["6M Return", market?.return6Month, true],
    ["1Y Return", market?.return1Year, true],
  ] as const;
  const points = (market?.history ?? []).map(row => ({ period: "Daily close", periodEnd: row.date, close: row.close }));
  return <section aria-labelledby={id} className="mt-8 space-y-5 border-t border-slate-200 pt-7">
    <div><h2 id={id} className="text-lg font-semibold">Market</h2>
      <p className="mt-1 text-xs text-slate-500">{ticker} · {market?.latestTradingDate ? `Latest daily close: ${dateLabel(market.latestTradingDate)}` : "Daily stock prices unavailable"} · USD</p>
    </div>
    {market?.status === "STALE" && <p role="status" className="text-sm text-amber-800">Showing previously reported prices. Market data may be out of date.</p>}
    {(!market || !["OK", "STALE"].includes(market.status)) && <p role="status" className="text-sm text-slate-500">Market data is currently unavailable. Financial statements remain available.</p>}
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{cards.map(([label, value, isReturn]) =>
      <article key={label} aria-label={label} className="panel p-5"><h3 className="text-sm font-medium text-slate-500">{label}</h3>
        <p className="mt-3 text-2xl font-semibold tabular-nums">{value == null || !Number.isFinite(value) ? "N/A" : isReturn ? percent(value) : money(value, false)}</p>
      </article>)}
    </div>
    <TrendChart key={ticker} title="Stock Price History" data={points} series={[{ key: "close", label: "Daily close", color: "#4f46e5" }]} />
    <p className="text-xs leading-relaxed text-slate-500">Source: {market?.source === "STASHGAMMA" ? "StashGamma" : market?.source || "Unavailable"}. End-of-day prices, not live quotes. Returns use provider closes and the preceding trading date for weekends or holidays; they are not total returns. Split/dividend adjustment policy is unverified. 52-week ranges require a full year of history. N/A means insufficient data.</p>
  </section>;
}
