import { useId } from "react";
import { money, periodLabel, type IncomeStatement } from "@/lib/financials";

export function TrendChart({ title, metric, periods }: { title: string; metric: "revenue" | "netIncome"; periods: IncomeStatement[] }) {
  const id = useId();
  const values = periods.map(row => row[metric]).filter((v): v is number => v !== null && Number.isFinite(v));
  const low = Math.min(0, ...values);
  const high = Math.max(0, ...values);
  const span = high - low || 1;
  const min = low < 0 ? low - span * 0.1 : 0;
  const max = high + span * 0.1;
  const x = (index: number) => 72 + index * 440 / Math.max(periods.length - 1, 1);
  const y = (value: number) => 205 - (value - min) / (max - min) * 175;
  const path = periods.map((row, index) => {
    const value = row[metric];
    if (value === null || !Number.isFinite(value)) return "";
    const previous = periods[index - 1]?.[metric];
    const connected = previous != null && Number.isFinite(previous);
    const segment = `${connected ? "L" : "M"}${x(index)},${y(value)}`;
    return segment;
  }).join(" ");
  return <section className="panel min-w-0 p-5 sm:p-6" aria-labelledby={id}>
    <div className="flex items-start justify-between gap-3"><div><h2 id={id} className="font-semibold">{title} trend</h2><p className="mt-1 text-xs text-slate-500">{periods.length} reporting periods · USD</p></div><span className="mt-2 h-2 w-2 rounded-full bg-indigo-600" aria-hidden="true" /></div>
    {values.length === 0 ? <div className="flex h-60 items-center justify-center text-sm text-slate-500">No {title.toLowerCase()} data available.</div> : <svg viewBox="0 0 550 250" className="mt-5 w-full" role="img" aria-label={`${title} trend. Exact values are available in the financial data table.`}>
      {[0, 1, 2, 3].map(i => { const value = min + (max - min) * i / 3; return <g key={i}><line x1="72" x2="512" y1={y(value)} y2={y(value)} stroke="#e2e8f0" strokeDasharray="3 4" /><text x="62" y={y(value) + 4} textAnchor="end" fontSize="11" fill="#64748b">{money(value)}</text></g>; })}
      {min < 0 && <line x1="72" x2="512" y1={y(0)} y2={y(0)} stroke="#94a3b8" />}
      <path d={path} fill="none" stroke="#4f46e5" strokeWidth="2.5" strokeLinejoin="round" />
      {periods.map((row, index) => <g key={`${row.periodStart}-${row.periodEnd}`}>
        {row[metric] !== null && Number.isFinite(row[metric]) && <circle cx={x(index)} cy={y(row[metric]!)} r="4" fill="#4f46e5" stroke="white" strokeWidth="2"><title>{`${periodLabel(row)}: ${money(row[metric], false)}`}</title></circle>}
        {(index === 0 || index === periods.length - 1 || index === Math.floor((periods.length - 1) / 2)) && <text x={x(index)} y="235" textAnchor={index === 0 ? "start" : index === periods.length - 1 ? "end" : "middle"} fontSize="11" fill="#64748b">{row.periodEnd}</text>}
      </g>)}
    </svg>}
  </section>;
}
