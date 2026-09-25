import { afterEach, expect, test } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { Dashboard } from "./dashboard";
import ErrorPage from "@/app/error";
import { DashboardLoading } from "./dashboard-loading";
import type { IncomeStatement } from "@/lib/financials";

afterEach(cleanup);
const company = { ticker: "TEST", name: "Test Company", sector: null, industry: null };
const statement = (period: string, periodEnd: string, revenue: number): IncomeStatement => ({
  period, periodStart: "2025-01-01", periodEnd, unit: "USD", revenue, netIncome: -100,
  costOfRevenue: null, grossProfit: null, operatingIncome: null,
});

test("switching frequency updates cards, charts, and table with date-matched balances", () => {
  render(<Dashboard company={company} income={[statement("FY", "2025-12-31", 1000), statement("Q1", "2026-03-31", 250)]} balances={[{ period: "POINT_IN_TIME", periodEnd: "2025-12-31", unit: "USD", cash: 75, assets: 500 }]} />);
  const cards = screen.getAllByRole("article");
  expect(within(cards[0]).getByText("$1K")).toBeTruthy();
  expect(within(cards[2]).getByText("$75")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Quarterly" }));
  expect(screen.getByRole("button", { name: "Quarterly" }).getAttribute("aria-pressed")).toBe("true");
  expect(within(cards[0]).getByText("$250")).toBeTruthy();
  expect(within(cards[2]).getByText("—")).toBeTruthy();
  expect(screen.getByRole("columnheader", { name: "Q1 · Mar 31, 2026" })).toBeTruthy();
  expect(screen.getAllByRole("img")).toHaveLength(2);
  expect(screen.queryByRole("columnheader", { name: /Dec 31, 2025/ })).toBeNull();
});

test("empty data renders clear messages without fabricated values", () => {
  render(<Dashboard company={company} income={[]} balances={[]} />);
  expect(screen.getByRole("status").textContent).toContain("No annual USD statements");
  expect(screen.getByText("No financial statements to display.")).toBeTruthy();
  expect(screen.getAllByText("—")).toHaveLength(4);
});

test("loading status is accessible and error retry invokes recovery", () => {
  const loading = render(<DashboardLoading />);
  expect(screen.getByRole("status").getAttribute("aria-label")).toBe("Loading company financials");
  loading.unmount();
  let retries = 0;
  render(<ErrorPage error={new Error("offline")} retry={() => { retries++; }} />);
  expect(screen.getByRole("alert")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Try again" }));
  expect(retries).toBe(1);
});
