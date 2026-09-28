import type { Summary } from "@/lib/dashboard";
import { dateLabel, money } from "@/lib/financials";

export function KpiCards({ summary }: { summary: Summary }) {
  const cards = [
    { key: "revenue", label: "Revenue", balance: false },
    { key: "netIncome", label: "Net income", balance: false },
    { key: "freeCashFlow", label: "Free cash flow", balance: false },
    { key: "cash", label: "Cash", balance: true },
    { key: "totalAssets", label: "Total assets", balance: true },
    { key: "totalLiabilities", label: "Total liabilities", balance: true },
  ] as const;
  return <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{cards.map(card => {
    const metric = summary[card.key];
    return <article key={card.key} className="panel p-5" aria-label={card.label}>
      <h3 className="text-sm font-medium text-slate-500">{card.label}</h3>
      <p className="mt-3 text-3xl font-semibold tracking-tight tabular-nums" title={metric.value == null ? "Not available" : money(metric.value, false)}>{metric.value == null ? "N/A" : money(metric.value)}</p>
      <p className="mt-3 text-xs text-slate-500">{metric.periodEnd ? `${card.balance ? "As of" : `${metric.period === "QUARTER" ? "Quarter" : metric.period} ended`} ${dateLabel(metric.periodEnd)}` : "No reported value"}</p>
    </article>;
  })}</div>;
}
