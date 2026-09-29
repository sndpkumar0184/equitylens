import "server-only";
import type { DashboardData } from "./dashboard";
import type { MarketData } from "./market";

export class ApiError extends Error {
  constructor(public status: number) { super(`Company API request failed (${status})`); }
}

async function get<T>(path: string): Promise<T> {
  const base = (process.env.BACKEND_URL || "http://localhost:8080").replace(/\/$/, "");
  const response = await fetch(`${base}/api/companies/${path}`, { cache: "no-store", signal: AbortSignal.timeout(120000) });
  if (!response.ok) throw new ApiError(response.status);
  return response.json() as Promise<T>;
}

export async function getDashboard(ticker: string): Promise<DashboardData> {
  return get<DashboardData>(`${encodeURIComponent(ticker)}/dashboard`);
}

export async function getMarket(ticker: string, interval: "DAILY" | "MONTHLY" | "YEARLY" = "DAILY"): Promise<MarketData | null> {
  try {
    return await get<MarketData>(`${encodeURIComponent(ticker)}/market?interval=${interval}`);
  } catch {
    // An independent market-provider outage must not take down SEC financials.
    return null;
  }
}
