import "server-only";
import type { Company, IncomeStatement, BalanceSheet } from "./financials";

async function get<T>(path: string): Promise<T> {
  const base = (process.env.BACKEND_URL || "http://localhost:8080").replace(/\/$/, "");
  const response = await fetch(`${base}/api/companies/${path}`, { cache: "no-store", signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error(`Company API request failed (${response.status})`);
  return response.json() as Promise<T>;
}

export async function getDashboard(ticker: string) {
  const path = encodeURIComponent(ticker);
  const [company, income, balances] = await Promise.all([
    get<Company>(path),
    get<IncomeStatement[]>(`${path}/financials/income-statement`),
    get<BalanceSheet[]>(`${path}/financials/balance-sheet`),
  ]);
  return { company, income, balances };
}
