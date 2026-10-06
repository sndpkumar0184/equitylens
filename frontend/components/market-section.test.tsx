import { afterEach, expect, test, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MarketSection } from "./market-section";
import type { MarketData } from "@/lib/market";

vi.mock("./realtime-market", () => ({ RealtimeMarket: () => null }));

vi.mock("lightweight-charts", () => {
  const series = { setData: vi.fn() };
  return { CandlestickSeries: {}, HistogramSeries: {}, ColorType: { Solid: "solid" }, createChart: () => ({
    addSeries: () => series, panes: () => [{}, { setHeight: vi.fn() }], timeScale: () => ({ fitContent: vi.fn() }),
    subscribeCrosshairMove: vi.fn(), remove: vi.fn(),
  }) };
});

afterEach(cleanup);
const data: MarketData = {
  ticker: "AAPL", source: "STASHGAMMA", currency: "USD", status: "OK", fetchedAt: null, retryAfter: null,
  priceBasis: "PROVIDER_CLOSE_UNVERIFIED_ADJUSTMENTS", latestPrice: 123.45, latestTradingDate: "2026-09-25",
  dailyChange: 2.3, dailyChangePercent: 1.91, latestVolume: 1000,
  high52Week: 150, low52Week: 90, return1Month: -5.25, return3Month: 0, return6Month: 12, return1Year: null,
  interval: "DAILY", supportedIntervals: ["DAILY", "WEEKLY", "MONTHLY", "YEARLY"],
  history: [{ date: "2026-09-25", open: 120, high: 125, low: 119, close: 123.45, adjustedClose: null, volume: 1000 }],
};

test("shows backend market summary, interval controls, source, and real OHLC chart", () => {
  render(<MarketSection ticker="AAPL" data={data} />);
  expect(screen.getAllByRole("article")).toHaveLength(9);
  expect(within(screen.getByRole("article", { name: "Stock Price" })).getByText("$123.45")).toBeTruthy();
  expect(within(screen.getByRole("article", { name: "1M Return" })).getByText("-5.25%")).toBeTruthy();
  expect(within(screen.getByRole("article", { name: "3M Return" })).getByText("0%")).toBeTruthy();
  expect(within(screen.getByRole("article", { name: "1Y Return" })).getByText("N/A")).toBeTruthy();
  expect(screen.getByText(/Latest close Sep 25, 2026/)).toBeTruthy();
  expect(screen.getByRole("img", { name: /daily Japanese candlestick chart with volume/ })).toBeTruthy();
  expect(screen.getByRole("button", { name: /Hourly/ }).hasAttribute("disabled")).toBe(true);
  expect(screen.getByRole("button", { name: "Monthly" }).hasAttribute("disabled")).toBe(false);
  expect(screen.getByRole("button", { name: "Weekly" }).hasAttribute("disabled")).toBe(false);
  expect(screen.getByText(/Source: Stashgamma/)).toBeTruthy();
});

test("missing or mismatched company market data shows N/A without fake prices", () => {
  const view = render(<MarketSection ticker="AAPL" data={null} />);
  expect(screen.getAllByText("N/A")).toHaveLength(9);
  expect(screen.queryByRole("img")).toBeNull();
  view.rerender(<MarketSection ticker="META" data={data} />);
  expect(screen.getAllByText("N/A")).toHaveLength(9);
  expect(screen.queryByText("$123.45")).toBeNull();
});

test("cached data is labeled stale and changing company replaces prices", () => {
  const view = render(<MarketSection ticker="AAPL" data={{ ...data, status: "STALE" }} />);
  expect(screen.getByRole("status").textContent).toContain("out of date");
  view.rerender(<MarketSection ticker="META" data={{ ...data, ticker: "META", latestPrice: 600, history: [] }} />);
  expect(within(screen.getByRole("article", { name: "Stock Price" })).getByText("$600")).toBeTruthy();
  expect(screen.queryByText("$123.45")).toBeNull();
});

test("monthly selection requests backend-aggregated market history", async () => {
  const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ ...data, interval: "MONTHLY", history: [] }), { status: 200, headers: { "content-type": "application/json" } }));
  render(<MarketSection ticker="AAPL" data={data} />);
  fireEvent.click(screen.getByRole("button", { name: "Monthly" }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/companies/AAPL/market?interval=MONTHLY", { cache: "no-store" }));
  fetchMock.mockRestore();
});
