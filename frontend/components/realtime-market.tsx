"use client";

import { useEffect, useState } from "react";

type Bar = { close: number; volume: number; vwap: number | null; lastEventTime: string; windowEnd: string; quality: string };
type Metrics = { return1mPct: number | null; return5mPct: number | null; rollingVolume: number | null;
  rollingVwap: number | null; volumeRatio: number | null; volatilityPct: number | null; anomaly: boolean; windowEnd: string };
type Reference = { price: number; eventTime: string };
export type LiveState = { symbol: string; status: string; provider: string; feed: string; synthetic: boolean;
  bar: Bar | null; analytics: Metrics | null; reference?: Reference | null; ingestion: { state: string } };
const format = (value: number | null | undefined, suffix = "") => value == null || !Number.isFinite(Number(value)) ? "N/A" : `${Number(value).toLocaleString("en-US", { maximumFractionDigits: 4 })}${suffix}`;

export function RealtimeMarket({ ticker }: { ticker: string }) {
  const [state, setState] = useState<LiveState | null>(null);
  const [connection, setConnection] = useState("Connecting");
  const [error, setError] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    let active = true;
    const abort = new AbortController();
    const accept = (data: LiveState) => { if (active && data.symbol === ticker) { setState(data); setError(false); } };
    fetch(`/api/market/realtime/${encodeURIComponent(ticker)}`, { signal: abort.signal, cache: "no-store" })
      .then(response => { if (!response.ok) throw new Error(); return response.json(); })
      .then(accept).catch(() => { if (active) setError(true); });
    const stream = typeof EventSource === "undefined" ? null : new EventSource(`/api/market/realtime/${encodeURIComponent(ticker)}/events`);
    if (stream) {
    stream.onopen = () => { if (active) setConnection("Connected"); };
    stream.onerror = () => { if (active) setConnection("Disconnected · reconnecting"); };
    stream.addEventListener("market", event => {
      try { accept(JSON.parse((event as MessageEvent).data)); } catch { if (active) setError(true); }
    });
    }
    const clock = setInterval(() => setNow(Date.now()), 15000);
    return () => { active = false; abort.abort(); stream?.close(); clearInterval(clock); };
  }, [ticker]);
  const data = state?.symbol === ticker ? state : null;
  const time = data?.reference?.eventTime || data?.bar?.lastEventTime;
  const stale = !!time && now - new Date(time).getTime() > 5 * 60000;
  const metrics = data?.analytics;
  const cards = [
    [data?.reference ? "Reference price" : "Latest finalized trade close", format(data?.reference?.price ?? data?.bar?.close, " USD")],
    ["1-minute return", format(metrics?.return1mPct, "%")], ["5-minute return", format(metrics?.return5mPct, "%")],
    ["5-minute volume (shares)", format(metrics?.rollingVolume)], ["5-minute VWAP", format(metrics?.rollingVwap, " USD")],
    ["Volume / prior 20-minute mean", format(metrics?.volumeRatio, "×")], ["5-return volatility", format(metrics?.volatilityPct, "%")],
  ];
  return <section aria-label="Realtime market intelligence" className="panel space-y-4 p-5">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div><p className="eyebrow">Live market intelligence</p><h2 className="mt-1 text-lg font-semibold">{ticker} streaming analytics</h2></div>
      <p aria-live="polite" className="text-xs text-slate-500">{connection}{stale ? " · analytics stale" : ""}</p>
    </div>
    {data?.synthetic && <p className="rounded bg-amber-50 p-2 text-sm text-amber-900">Synthetic development data</p>}
    {error && <p role="alert" className="text-sm text-amber-800">Realtime analytics are unavailable.</p>}
    {!error && !time && <p className="text-sm text-slate-500">{data ? "No realtime observations for this source. Historical data remains available." : "Loading realtime analytics…"}</p>}
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{cards.map(([label, value]) =>
      <div key={label} className="rounded border border-slate-100 p-3"><p className="text-xs text-slate-500">{label}</p><p className="mt-2 font-semibold tabular-nums">{value}</p></div>)}</div>
    <div className="border-t border-slate-100 pt-3 text-xs leading-relaxed text-slate-500">
      <p>Source: {data ? `${data.provider} / ${data.feed}` : "Awaiting source"}. {data?.feed?.includes("IEX") ? "IEX coverage is a partial market feed." : "Coverage depends on the configured feed."}</p>
      <p>Last market event: {time ? new Date(time).toLocaleString() : "N/A"}. Ingestion: {data?.ingestion.state ?? "Unknown"}.</p>
      <p>Trade analytics use finalized event-time windows with a lateness allowance. Missing metrics need more consecutive observations. Reference prices do not provide volume or VWAP. Statistics describe observed activity and do not establish its cause.</p>
    </div>
    <div aria-label="Live market activity" className="rounded bg-slate-50 p-3 text-sm text-slate-600">
      <p className="font-medium">Live market activity</p>
      <p className="mt-1">{metrics?.anomaly ? `${new Date(metrics.windowEnd).toLocaleTimeString()} · Unusual observed volume: at least 3× the prior 20-minute mean.` : "No volume anomaly in the latest finalized window."}</p>
    </div>
  </section>;
}
