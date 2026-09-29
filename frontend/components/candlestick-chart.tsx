"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { CandlestickSeries, ColorType, createChart, HistogramSeries, type MouseEventParams, type Time } from "lightweight-charts";
import type { DailyPrice, MarketInterval } from "@/lib/market";

type Candle = DailyPrice & { open: number; high: number; low: number };
const currency = (value: number) => new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 2 }).format(value);
const volumeLabel = (value: number) => new Intl.NumberFormat("en-US", { notation: "compact", maximumFractionDigits: 1 }).format(value);

export function CandlestickChart({ history, interval, loading, error }: { history: DailyPrice[]; interval: MarketInterval; loading: boolean; error: string | null }) {
  const chartRef = useRef<HTMLDivElement>(null);
  const [selected, setSelected] = useState<Candle | null>(null);
  const candles = useMemo(() => history.filter((row): row is Candle =>
    [row.open, row.high, row.low, row.close].every(value => typeof value === "number" && Number.isFinite(value))), [history]);

  useEffect(() => {
    if (!chartRef.current || candles.length === 0) return;
    let chart: ReturnType<typeof createChart> | undefined;
    let cancelled = false;
    void import("lightweight-charts").then(() => {
      if (cancelled || !chartRef.current) return;
      chart = createChart(chartRef.current, {
        autoSize: true, height: 430,
        layout: { background: { color: "#ffffff", type: ColorType.Solid }, attributionLogo: true, textColor: "#64748b", fontFamily: "Arial, sans-serif", fontSize: 12,
          panes: { separatorColor: "#e2e8f0", separatorHoverColor: "#cbd5e1", enableResize: false } },
        grid: { vertLines: { color: "#f1f5f9" }, horzLines: { color: "#f1f5f9" } },
        crosshair: { mode: 0 }, rightPriceScale: { borderColor: "#e2e8f0", scaleMargins: { top: 0.12, bottom: 0.12 } },
        timeScale: { borderColor: "#e2e8f0", timeVisible: interval === "HOURLY", rightOffset: 3, fixLeftEdge: true },
        handleScroll: { mouseWheel: true, pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: false },
        handleScale: { axisPressedMouseMove: true, mouseWheel: true, pinch: true },
      });
      const priceSeries = chart.addSeries(CandlestickSeries, {
        upColor: "#16846b", downColor: "#d45d4c", borderVisible: false,
        wickUpColor: "#16846b", wickDownColor: "#d45d4c", priceLineVisible: true,
      });
      const volumeSeries = chart.addSeries(HistogramSeries, { priceFormat: { type: "volume" }, priceScaleId: "" }, 1);
      const candleRows = candles.map(row => ({ time: row.date as Time, open: row.open, high: row.high, low: row.low, close: row.close }));
      priceSeries.setData(candleRows);
      volumeSeries.setData(candles.filter(row => row.volume !== null).map(row => ({ time: row.date as Time, value: row.volume!, color: row.close >= row.open ? "rgba(22,132,107,0.32)" : "rgba(212,93,76,0.32)" })));
      if (chart.panes().length > 1) chart.panes()[1].setHeight(86);
      chart.timeScale().fitContent();
      chart.subscribeCrosshairMove((param: MouseEventParams<Time>) => {
        const item = param.seriesData.get(priceSeries);
        if (!item || !("open" in item) || !param.time) { setSelected(null); return; }
        const time = typeof param.time === "string" ? param.time : "year" in param.time ? `${param.time.year}-${String(param.time.month).padStart(2, "0")}-${String(param.time.day).padStart(2, "0")}` : "";
        const found = candles.find(row => row.date === time);
        if (found) setSelected(found);
      });
    });
    return () => { cancelled = true; chart?.remove(); };
  }, [candles, interval]);

  const active = selected && candles.some(row => row.date === selected.date) ? selected : candles.at(-1) ?? null;
  return <section className="panel min-w-0 overflow-hidden" aria-labelledby="price-chart-title">
    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4 sm:px-6">
      <div><h2 id="price-chart-title" className="font-semibold text-slate-900">Price history</h2><p className="mt-1 text-xs text-slate-500">Japanese candlesticks · USD · Volume</p></div>
      {active && <dl aria-live="polite" className="flex flex-wrap gap-x-4 gap-y-1 text-xs tabular-nums">
        <div><dt className="inline text-slate-500">Date </dt><dd className="inline font-semibold">{active.date}</dd></div>
        <div><dt className="inline text-slate-500">O </dt><dd className="inline font-semibold">{currency(active.open)}</dd></div>
        <div><dt className="inline text-slate-500">H </dt><dd className="inline font-semibold">{currency(active.high)}</dd></div>
        <div><dt className="inline text-slate-500">L </dt><dd className="inline font-semibold">{currency(active.low)}</dd></div>
        <div><dt className="inline text-slate-500">C </dt><dd className="inline font-semibold">{currency(active.close)}</dd></div>
        <div><dt className="inline text-slate-500">Vol </dt><dd className="inline font-semibold">{active.volume === null ? "N/A" : volumeLabel(active.volume)}</dd></div>
      </dl>}
    </div>
    {loading ? <div role="status" className="flex h-[430px] items-center justify-center text-sm text-slate-500">Loading price history…</div>
      : error ? <div role="alert" className="flex h-[430px] items-center justify-center px-6 text-center text-sm text-rose-700">{error}</div>
      : candles.length === 0 ? <div role="status" className="flex h-[430px] items-center justify-center px-6 text-center text-sm text-slate-500">No complete OHLC observations are available for this period.</div>
      : <div ref={chartRef} role="img" aria-label={`${interval.toLowerCase()} Japanese candlestick chart with volume`} className="h-[430px] w-full" />}
  </section>;
}
