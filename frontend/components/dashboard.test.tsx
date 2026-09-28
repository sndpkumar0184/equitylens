import { afterEach, expect, test } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { Dashboard } from "./dashboard";
import ErrorPage from "@/app/error";
import { DashboardLoading } from "./dashboard-loading";
import type { PeriodData, MetricValue } from "@/lib/dashboard";

afterEach(cleanup);
const snapshot = (value: number | null, period = "FY", periodEnd = "2025-12-31"): MetricValue => ({ value, period, periodEnd, periodStart: "2025-01-01" });
const company = (ticker: string) => ({ ticker, name: `${ticker} Company`, sector: null, industry: null });
const data = (revenue = 1000, period = "FY", periodEnd = "2025-12-31"): PeriodData => ({
  summary: { revenue: snapshot(revenue, period, periodEnd), netIncome: snapshot(-100, period, periodEnd), freeCashFlow: snapshot(200, period, periodEnd), cash: snapshot(75, period, periodEnd), totalAssets: snapshot(500, period, periodEnd), totalLiabilities: snapshot(250, period, periodEnd) },
  income: [{ period, periodStart: "2025-01-01", periodEnd, unit: "USD", revenue, netIncome: -100, costOfRevenue: null, grossProfit: null, operatingIncome: null }],
  growth: [{ period, periodStart: "2025-01-01", periodEnd, revenueGrowth: 20 }],
  profitability: [{ period, periodStart: "2025-01-01", periodEnd, grossMargin: null, operatingMargin: null, netMargin: -10 }],
  cashFlow: [{ period, periodStart: "2025-01-01", periodEnd, unit: "USD", operatingCashFlow: 300, capitalExpenditures: 100, freeCashFlow: 200 }],
  balances: [{ period, periodEnd, unit: "USD", cash: 75, assets: 500, liabilities: 250, debt: null }],
});

test.each(["META", "AAPL"])("renders %s, six charts, KPI dates, and unavailable margins/debt", ticker => {
  render(<Dashboard company={company(ticker)} annual={data()} quarterly={data(250, "Q1", "2026-03-31")} />);
  expect(screen.getByRole("heading", { name: `${ticker} Company` })).toBeTruthy();
  expect(screen.getAllByRole("article")).toHaveLength(6);
  expect(within(screen.getByRole("article", { name: "Revenue" })).getByText("$1K")).toBeTruthy();
  expect(within(screen.getByRole("article", { name: "Revenue" })).getByText("FY ended Dec 31, 2025")).toBeTruthy();
  expect(screen.getAllByRole("img")).toHaveLength(6);
  expect(screen.getByText(/Gross margin unavailable/)).toBeTruthy();
  expect(screen.getByText("Total debt unavailable")).toBeTruthy();
});

test("switching frequency changes cards, chart values, and table together", () => {
  render(<Dashboard company={company("META")} annual={data()} quarterly={data(250, "Q1", "2026-03-31")} />);
  fireEvent.click(screen.getByRole("button", { name: "Quarterly" }));
  expect(screen.getByRole("button", { name: "Quarterly" }).getAttribute("aria-pressed")).toBe("true");
  expect(within(screen.getByRole("article", { name: "Revenue" })).getByText("$250")).toBeTruthy();
  expect(screen.getByRole("columnheader", { name: "Q1 · Mar 31, 2026" })).toBeTruthy();
  expect(screen.queryByRole("columnheader", { name: /Dec 31, 2025/ })).toBeNull();
});

test("changing company replaces KPI and chart data without retaining the previous ticker", () => {
  const view = render(<Dashboard company={company("META")} annual={data()} quarterly={data()} />);
  view.rerender(<Dashboard company={company("AAPL")} annual={data(2000)} quarterly={data(500)} />);
  expect(screen.queryByRole("heading", { name: "META Company" })).toBeNull();
  expect(within(screen.getByRole("article", { name: "Revenue" })).getByText("$2K")).toBeTruthy();
  const chart = screen.getByRole("region", { name: "Revenue & Net Income" });
  expect(within(chart).getByText("$2,000")).toBeTruthy();
});

test("empty data shows N/A, chart explanations, and table empty state", () => {
  const empty = data();
  for (const key of Object.keys(empty.summary) as (keyof typeof empty.summary)[]) empty.summary[key] = { value: null, period: null, periodStart: null, periodEnd: null };
  empty.income = []; empty.growth = []; empty.profitability = []; empty.cashFlow = []; empty.balances = [];
  render(<Dashboard company={company("TEST")} annual={empty} quarterly={empty} />);
  expect(screen.getByRole("status").textContent).toContain("No annual USD statements");
  expect(screen.getByText("No financial statements to display.")).toBeTruthy();
  expect(screen.getAllByText("N/A")).toHaveLength(6);
  expect(screen.getByText("Insufficient comparable revenue history.")).toBeTruthy();
});

test("loading is accessible and error retry invokes recovery", () => {
  const loading = render(<DashboardLoading />);
  expect(screen.getByRole("status").textContent).toContain("Loading company data");
  loading.unmount();
  let retries = 0;
  render(<ErrorPage error={new Error("offline")} retry={() => { retries++; }} />);
  fireEvent.click(screen.getByRole("button", { name: "Try again" }));
  expect(retries).toBe(1);
});
