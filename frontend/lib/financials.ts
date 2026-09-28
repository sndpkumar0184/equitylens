export type Company = { ticker: string; name: string; sector: string | null; industry: string | null };
export type IncomeStatement = {
  period: string; periodStart: string; periodEnd: string; unit: string;
  revenue: number | null; costOfRevenue: number | null; grossProfit: number | null;
  operatingIncome: number | null; netIncome: number | null;
};
export type BalanceSheet = { period: string; periodEnd: string; unit: string; cash: number | null; assets: number | null };
export type Frequency = "annual" | "quarterly";

export function selectPeriods(income: IncomeStatement[], frequency: Frequency) {
  return income.filter(row => row.unit === "USD" && (frequency === "annual" ? row.period === "FY" : /^(Q[1-4]|QUARTER)$/.test(row.period)))
    .sort((a, b) => a.periodEnd.localeCompare(b.periodEnd) || a.periodStart.localeCompare(b.periodStart));
}

export function balanceAt(balances: BalanceSheet[], date: string | undefined) {
  return balances.find(row => row.periodEnd === date && row.unit === "USD");
}

export function money(value: number | null | undefined, compact = true) {
  if (value == null || !Number.isFinite(value)) return "—";
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", notation: compact ? "compact" : "standard", minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(value);
}

export function dateLabel(date: string) {
  return new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric", timeZone: "UTC" }).format(new Date(`${date}T00:00:00Z`));
}

export function periodLabel(row: IncomeStatement) {
  // Reporting dates do not necessarily correspond to the fiscal year number.
  return `${row.period === "QUARTER" ? "Quarter" : row.period} · ${dateLabel(row.periodEnd)}`;
}
