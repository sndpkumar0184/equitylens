import { redirect } from "next/navigation";
import { companyPath, normalizeTicker } from "@/lib/ticker";

export default async function Home({ searchParams }: { searchParams: Promise<{ ticker?: string | string[] }> }) {
  const query = await searchParams;
  if (typeof query.ticker === "string") {
    const ticker = normalizeTicker(query.ticker);
    if (ticker) redirect(companyPath(ticker));
    return <div className="panel p-8" role="alert">Invalid ticker. Enter a valid stock symbol in the search above.</div>;
  }
  return <section className="panel px-6 py-16 text-center sm:px-12">
    <p className="eyebrow">Company research</p>
    <h1 className="mt-4 text-3xl font-semibold tracking-tight sm:text-4xl">A clearer view of company financials.</h1>
    <p className="mx-auto mt-5 max-w-xl text-slate-600">Search for a stock ticker above to explore its revenue, earnings, balance sheet, and reporting history. Company data is loaded from SEC filings on your first visit.</p>
  </section>;
}
