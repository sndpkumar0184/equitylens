import { afterEach, expect, test } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { MarketSection } from "./market-section";
import type { MarketData } from "@/lib/market";

afterEach(cleanup);
const data: MarketData = {
  ticker: "AAPL", source: "STASHGAMMA", currency: "USD", status: "OK", fetchedAt: null, retryAfter: null,
  priceBasis: "PROVIDER_CLOSE_UNVERIFIED_ADJUSTMENTS", latestPrice: 123.45, latestTradingDate: "2026-09-25",
  high52Week: 150, low52Week: 90, return1Month: -5.25, return3Month: 0, return6Month: 12, return1Year: null,
  history: [{ date: "2026-09-25", open: 120, high: 125, low: 119, close: 123.45, adjustedClose: null, volume: 1000 }],
};

test("shows seven backend-supplied market cards, date, source, and price chart", () => {
  render(<MarketSection ticker="AAPL" data={data} />);
  expect(screen.getAllByRole("article")).toHaveLength(7);
  expect(within(screen.getByRole("article", { name: "Stock Price" })).getByText("$123.45")).toBeTruthy();
  expect(within(screen.getByRole("article", { name: "1M Return" })).getByText("-5.25%")).toBeTruthy();
  expect(within(screen.getByRole("article", { name: "3M Return" })).getByText("0%")).toBeTruthy();
  expect(within(screen.getByRole("article", { name: "1Y Return" })).getByText("N/A")).toBeTruthy();
  expect(screen.getByText(/Latest daily close: Sep 25, 2026/)).toBeTruthy();
  expect(screen.getByRole("img", { name: /Stock Price History/ })).toBeTruthy();
  expect(screen.getByText(/Source: StashGamma/)).toBeTruthy();
});

test("missing or mismatched company market data shows N/A without fake prices", () => {
  const view = render(<MarketSection ticker="AAPL" data={null} />);
  expect(screen.getAllByText("N/A")).toHaveLength(7);
  expect(screen.queryByRole("img")).toBeNull();
  view.rerender(<MarketSection ticker="META" data={data} />);
  expect(screen.getAllByText("N/A")).toHaveLength(7);
  expect(screen.queryByText("$123.45")).toBeNull();
});

test("cached data is labeled stale and changing company replaces prices", () => {
  const view = render(<MarketSection ticker="AAPL" data={{ ...data, status: "STALE" }} />);
  expect(screen.getByRole("status").textContent).toContain("out of date");
  view.rerender(<MarketSection ticker="META" data={{ ...data, ticker: "META", latestPrice: 600, history: [] }} />);
  expect(within(screen.getByRole("article", { name: "Stock Price" })).getByText("$600")).toBeTruthy();
  expect(screen.queryByText("$123.45")).toBeNull();
});
