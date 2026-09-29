import { Suspense } from "react";
import { notFound, redirect } from "next/navigation";
import { Dashboard } from "@/components/dashboard";
import { DashboardLoading } from "@/components/dashboard-loading";
import { ApiError, getDashboard } from "@/lib/api";
import { companyPath, normalizeTicker } from "@/lib/ticker";

async function CompanyDashboard({ ticker }: { ticker: string }) {
  let data;
  try {
    data = await getDashboard(ticker);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
  return <Dashboard key={ticker} {...data} />;
}

export default async function CompanyPage({ params }: { params: Promise<{ ticker: string }> }) {
  const { ticker: input } = await params;
  const ticker = normalizeTicker(input);
  if (!ticker) return <div className="panel p-8" role="alert">Invalid ticker. Use the search above to enter 1–10 letters, numbers, dots or hyphens.</div>;
  if (ticker !== input) redirect(companyPath(ticker));
  return <Suspense key={ticker} fallback={<DashboardLoading />}><CompanyDashboard ticker={ticker} /></Suspense>;
}
