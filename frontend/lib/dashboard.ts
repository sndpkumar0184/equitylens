import type { Company, IncomeStatement, BalanceSheet } from "./financials";

export type MetricValue = { value: number | null; period: string | null; periodStart: string | null; periodEnd: string | null };
export type Summary = Record<"revenue" | "netIncome" | "freeCashFlow" | "cash" | "totalAssets" | "totalLiabilities", MetricValue>;
type Period = { period: string; periodStart: string; periodEnd: string };
export type Growth = Period & { revenueGrowth: number | null };
export type Profitability = Period & { grossMargin: number | null; operatingMargin: number | null; netMargin: number | null };
export type CashFlow = Period & { unit: string; operatingCashFlow: number | null; capitalExpenditures: number | null; freeCashFlow: number | null };
export type DashboardBalance = BalanceSheet & { debt: number | null; liabilities: number | null };
export type PeriodData = { summary: Summary; income: IncomeStatement[]; growth: Growth[]; profitability: Profitability[]; cashFlow: CashFlow[]; balances: DashboardBalance[] };
export type DashboardData = { company: Company; annual: PeriodData; quarterly: PeriodData };

export function percent(value: number | null | undefined) {
  return value == null || !Number.isFinite(value) ? "N/A" : `${new Intl.NumberFormat("en-US", { maximumFractionDigits: 2 }).format(value)}%`;
}
