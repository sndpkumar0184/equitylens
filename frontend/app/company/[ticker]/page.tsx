import { Suspense } from "react";
import { notFound, redirect } from "next/navigation";
import { Dashboard } from "@/components/dashboard";
import { DashboardLoading } from "@/components/dashboard-loading";
import { ApiError, getDashboard, getMarket } from "@/lib/api";
import { MarketSection } from "@/components/market-section";
import { companyPath, normalizeTicker } from "@/lib/ticker";

async function CompanyDashboard({ ticker }: { ticker: string }) {
  let data;
  try {
    data = await getDashboard(ticker);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
  return <><Dashboard key={ticker} {...data} />
    <Suspense fallback={<p role="status" className="mt-8 text-sm text-slate-500">Loading market prices…</p>}>
      <CompanyMarket ticker={ticker} />
    </Suspense>
  </>;
}

async function CompanyMarket({ ticker }: { ticker: string }) {
  return <MarketSection ticker={ticker} data={await getMarket(ticker)} />;
}

export default async function CompanyPage({ params }: { params: Promise<{ ticker: string }> }) {
  const { ticker: input } = await params;
  const ticker = normalizeTicker(input);
  if (!ticker) return <div className="panel p-8" role="alert">Invalid ticker. Use the search above to enter 1–10 letters, numbers, dots or hyphens.</div>;
  if (ticker !== input) redirect(companyPath(ticker));
  return <Suspense key={ticker} fallback={<DashboardLoading />}><CompanyDashboard ticker={ticker} /></Suspense>;
}
