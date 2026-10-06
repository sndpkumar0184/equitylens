"use client";

import { useId, useState } from "react";
import type { MarketData, MarketInterval } from "@/lib/market";
import { dateLabel, money } from "@/lib/financials";
import { percent } from "@/lib/dashboard";
import { RealtimeMarket } from "./realtime-market";
import { CandlestickChart } from "./candlestick-chart";

const intervals: { value: MarketInterval; label: string }[] = [
  { value: "HOURLY", label: "Hourly" }, { value: "DAILY", label: "Daily" }, { value: "WEEKLY", label: "Weekly" },
  { value: "MONTHLY", label: "Monthly" }, { value: "YEARLY", label: "Yearly" },
];
const sourceLabel = (value: string | undefined) => value?.toLowerCase().replace(/(^|[_\s-])\w/g, part => part.toUpperCase()) || "Unavailable";

export function MarketSection({ ticker, data }: { ticker: string; data: MarketData | null }) {
  return <MarketSectionContent key={ticker} ticker={ticker} data={data} />;
}

function MarketSectionContent({ ticker, data }: { ticker: string; data: MarketData | null }) {
  const id = useId();
  const [market, setMarket] = useState(data?.ticker === ticker ? data : null);
  const [interval, setInterval] = useState<MarketInterval>("DAILY");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const activeMarket = market?.ticker === ticker ? market : null;
  const supported = new Set(activeMarket?.supportedIntervals ?? []);

  async function chooseInterval(next: MarketInterval) {
    if (next === "HOURLY" || next === interval || !supported.has(next)) return;
    setInterval(next); setLoading(true); setError(null);
    try {
      const response = await fetch(`/api/companies/${encodeURIComponent(ticker)}/market?interval=${next}`, { cache: "no-store" });
      if (!response.ok) throw new Error("Price history could not be loaded. Please try again.");
      const result = await response.json() as MarketData;
      if (result.ticker !== ticker) throw new Error("Price history returned for a different ticker.");
      setMarket(result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Price history could not be loaded.");
    } finally { setLoading(false); }
  }

  const cards: { label: string; value: number | null | undefined; format?: "return" | "volume"; secondary?: string }[] = [
    { label: "Stock Price", value: activeMarket?.latestPrice },
    { label: "Daily Change", value: activeMarket?.dailyChange, secondary: activeMarket?.dailyChangePercent == null ? undefined : percent(activeMarket.dailyChangePercent) },
    { label: "Volume", value: activeMarket?.latestVolume, format: "volume" },
    { label: "52W High", value: activeMarket?.high52Week }, { label: "52W Low", value: activeMarket?.low52Week },
    { label: "1M Return", value: activeMarket?.return1Month, format: "return" },
    { label: "3M Return", value: activeMarket?.return3Month, format: "return" },
    { label: "6M Return", value: activeMarket?.return6Month, format: "return" },
    { label: "1Y Return", value: activeMarket?.return1Year, format: "return" },
  ];
  const valueLabel = (value: number | null | undefined, format?: "return" | "volume") => {
    if (value == null || !Number.isFinite(value)) return "N/A";
    if (format === "return") return percent(value);
    if (format === "volume") return new Intl.NumberFormat("en-US", { notation: "compact", maximumFractionDigits: 1 }).format(value);
    return money(value, false);
  };
  return <section aria-labelledby={id} className="space-y-6">
    <div className="flex flex-col justify-between gap-4 border-b border-slate-200 pb-5 sm:flex-row sm:items-end">
      <div><p className="eyebrow">Market data</p><h1 id={id} className="mt-2 text-2xl font-semibold tracking-tight sm:text-3xl">{ticker} price history</h1>
        <p className="mt-2 text-sm text-slate-500">{activeMarket?.latestTradingDate ? `Latest close ${dateLabel(activeMarket.latestTradingDate)}` : "Historical end-of-day prices"} · USD</p></div>
      {activeMarket?.status === "STALE" && <p role="status" className="text-sm text-amber-800">Showing previously reported prices; market data may be out of date.</p>}
    </div>
    {(!activeMarket || !["OK", "STALE"].includes(activeMarket.status)) && <p role="status" className="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600">Market data is currently unavailable. Financial statements remain available.</p>}
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
      {cards.map(({ label, value, format, secondary }) => <article key={label} aria-label={label} className="panel min-w-0 px-4 py-4 sm:px-5">
        <h2 className="text-xs font-medium text-slate-500">{label}</h2>
        <p className="mt-2 truncate text-xl font-semibold tracking-tight tabular-nums text-slate-900">{valueLabel(value, format)}</p>
        {secondary && <p className={`mt-1 text-xs font-medium tabular-nums ${Number(activeMarket?.dailyChangePercent) >= 0 ? "text-emerald-700" : "text-rose-700"}`}>{secondary}</p>}
      </article>)}
    </div>
    <RealtimeMarket key={ticker} ticker={ticker} />
    <section aria-label="Chart timeframe" className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm font-medium text-slate-700">Historical price</p>
        <div role="group" aria-label="Price chart interval" className="inline-flex max-w-full flex-wrap gap-1 rounded-lg border border-slate-200 bg-white p-1">
          {intervals.map(item => {
            const isSupported = supported.has(item.value);
            return <button key={item.value} type="button" aria-pressed={interval === item.value}
              aria-label={`${item.label}${item.value === "HOURLY" && !isSupported ? ", unavailable for current provider" : ""}`}
              title={item.value === "HOURLY" && !isSupported ? "Hourly history is unavailable from the current provider" : undefined}
              disabled={!isSupported || loading} onClick={() => void chooseInterval(item.value)}
              className={`rounded-md px-3 py-2 text-xs font-semibold transition-colors disabled:cursor-not-allowed disabled:text-slate-300 ${interval === item.value ? "bg-slate-900 text-white" : "text-slate-600 hover:bg-slate-100"}`}>
              {item.label}{item.value === "HOURLY" && !isSupported ? " · unavailable" : ""}
            </button>;
          })}
        </div>
      </div>
      <CandlestickChart history={activeMarket?.history ?? []} interval={interval} loading={loading} error={error} />
    </section>
    <p className="text-xs leading-relaxed text-slate-500">Source: {sourceLabel(activeMarket?.source)}. End-of-day prices, not live quotes. Weekly, monthly and yearly candles aggregate daily observations; hourly data is unavailable from this provider. Returns use the closest available prior trading session, allow up to seven days for weekends or market holidays, and are price returns rather than total returns. Price adjustment policy is unverified. 52-week ranges require a full year of history.</p>
  </section>;
}
