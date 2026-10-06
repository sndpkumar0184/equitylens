import Link from "next/link";
import { companyPath } from "@/lib/ticker";

export function CompanyTabs({ ticker, active }: { ticker: string; active: "overview" | "market" | "assistant" }) {
  const tabs = [{ key: "overview" as const, label: "Overview", href: companyPath(ticker) },
    { key: "market" as const, label: "Market", href: `${companyPath(ticker)}/market` },
    { key: "assistant" as const, label: "AI Assistant", href: `${companyPath(ticker)}/assistant` }];
  return <nav aria-label="Company sections" className="mb-7 flex gap-1 border-b border-slate-200">
    {tabs.map(tab => <Link key={tab.key} href={tab.href} aria-current={active === tab.key ? "page" : undefined}
      className={`-mb-px border-b-2 px-4 py-3 text-sm font-medium transition-colors ${active === tab.key ? "border-indigo-600 text-indigo-700" : "border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-800"}`}>
      {tab.label}
    </Link>)}
  </nav>;
}
