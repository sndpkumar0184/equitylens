import { Suspense } from "react";
import { Dashboard } from "@/components/dashboard";
import { DashboardLoading } from "@/components/dashboard-loading";
import { getDashboard } from "@/lib/api";

async function CompanyDashboard({ ticker }: { ticker: string }) {
  const data = await getDashboard(ticker);
  return <Dashboard {...data} />;
}

export default async function Home({ searchParams }: {
  searchParams: Promise<{ ticker?: string | string[] }>;
}) {
  const query = await searchParams;
  const ticker = (typeof query.ticker === "string" ? query.ticker : "META").trim().toUpperCase();
  if (!/^[A-Z0-9][A-Z0-9.-]{0,9}$/.test(ticker)) {
    return <div className="panel p-8" role="alert">Enter a valid ticker of up to 10 letters, numbers, dots, or hyphens.</div>;
  }
  return <Suspense key={ticker} fallback={<DashboardLoading />}><CompanyDashboard ticker={ticker} /></Suspense>;
}
