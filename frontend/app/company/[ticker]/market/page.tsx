import { Suspense } from "react";
import { redirect } from "next/navigation";
import { CompanyTabs } from "@/components/company-tabs";
import { MarketSection } from "@/components/market-section";
import { getMarket } from "@/lib/api";
import { companyPath, normalizeTicker } from "@/lib/ticker";

async function CompanyMarketData({ ticker }: { ticker: string }) {
  return <MarketSection ticker={ticker} data={await getMarket(ticker)} />;
}

export default async function CompanyMarketPage({ params }: { params: Promise<{ ticker: string }> }) {
  const { ticker: input } = await params;
  const ticker = normalizeTicker(input);
  if (!ticker) return <div className="panel p-8" role="alert">Invalid ticker. Use the search above to enter a valid symbol.</div>;
  if (ticker !== input) redirect(`${companyPath(ticker)}/market`);
  return <><CompanyTabs ticker={ticker} active="market" />
    <Suspense fallback={<p role="status" className="panel px-5 py-8 text-sm text-slate-500">Loading market prices…</p>}>
      <CompanyMarketData ticker={ticker} />
    </Suspense>
  </>;
}
