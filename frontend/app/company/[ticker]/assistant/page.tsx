import { redirect } from "next/navigation";
import { CompanyTabs } from "@/components/company-tabs";
import { AIAssistant } from "@/components/ai-assistant";
import { companyPath, normalizeTicker } from "@/lib/ticker";

export default async function CompanyAssistantPage({ params }: { params: Promise<{ ticker: string }> }) {
  const { ticker: input } = await params;
  const ticker = normalizeTicker(input);
  if (!ticker) return <div className="panel p-8" role="alert">Invalid ticker. Use the company search to enter a valid symbol.</div>;
  if (ticker !== input) redirect(`${companyPath(ticker)}/assistant`);
  return <><CompanyTabs ticker={ticker} active="assistant" /><AIAssistant key={ticker} ticker={ticker} /></>;
}
