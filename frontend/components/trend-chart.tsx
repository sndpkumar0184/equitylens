"use client";

import { useId, useState } from "react";
import { dateLabel, money } from "@/lib/financials";
import { percent } from "@/lib/dashboard";

type ChartPeriod = { period: string; periodEnd: string; periodStart?: string };
type Series<T> = { key: keyof T; label: string; color: string };

export function TrendChart<T extends ChartPeriod>({ title, data, series, format = "money", note }: {
  title: string; data: T[]; series: Series<T>[]; format?: "money" | "percent"; note?: string;
}) {
  const id = useId();
  const [active, setActive] = useState<number | null>(null);
  const valueAt = (row: T, key: keyof T): number | null => {
    const value = row[key];
    return typeof value === "number" && Number.isFinite(value) ? value : null;
  };
  const formatted = (value: number | null, compact = true) => format === "percent" ? percent(value) : value == null ? "N/A" : money(value, compact);
  const available = series.filter(s => data.some(row => valueAt(row, s.key) !== null));
  const missing = series.filter(s => !available.includes(s));
  const values = data.flatMap(row => available.map(s => valueAt(row, s.key))).filter((v): v is number => v !== null);
  const low = Math.min(0, ...values);
  const high = Math.max(0, ...values);
  const span = high - low || 1;
  const min = low < 0 ? low - span * 0.08 : 0;
  const max = high + span * 0.08;
  const timestamps = data.map(row => Date.parse(row.periodEnd));
  const first = timestamps[0] || 0;
  const last = timestamps.at(-1) || first;
  const x = (index: number) => last === first ? 298 : 76 + (timestamps[index] - first) / (last - first) * 444;
  const y = (value: number) => 170 - (value - min) / (max - min) * 150;
  const selected = Math.min(active ?? data.length - 1, data.length - 1);
  const current = data[selected];
  const label = (row: T) => `${row.period === "QUARTER" ? "Quarter" : row.period} · ${dateLabel(row.periodEnd)}`;
  return <section className="panel min-w-0 p-5 sm:p-6" aria-labelledby={id}>
    <h2 id={id} className="font-semibold">{title}</h2>
    <p className="mt-1 text-xs text-slate-500">{format === "percent" ? "Percent" : "USD"} · {data.length} reporting periods</p>
    <ul aria-label={`${title} series`} className="mt-3 flex flex-wrap gap-x-4 gap-y-2 text-xs text-slate-600">
      {available.map(s => <li key={String(s.key)} className="flex items-center gap-1.5"><span aria-hidden="true" className="h-2 w-2 rounded-full" style={{ background: s.color }} />{s.label}</li>)}
    </ul>
    {values.length === 0 ? <div className="flex min-h-48 items-center justify-center text-center text-sm text-slate-500">{format === "percent" && title === "Revenue Growth" ? "Insufficient comparable revenue history." : "No reported data available for this chart."}</div> : <>
      <svg viewBox="0 0 550 210" className="mt-3 w-full" role="img" aria-label={`${title}. Focus or select a reporting period to inspect exact values.`}>
        {[0, 1, 2, 3].map(i => { const value = min + (max - min) * i / 3; return <g key={i}>
          <line x1="76" x2="520" y1={y(value)} y2={y(value)} stroke="#e2e8f0" strokeDasharray="3 4" />
          <text x="66" y={y(value) + 4} textAnchor="end" fontSize="11" fill="#64748b">{formatted(value)}</text>
        </g>; })}
        {min < 0 && <line x1="76" x2="520" y1={y(0)} y2={y(0)} stroke="#94a3b8" />}
        {available.map(s => {
          const path = data.map((row, index) => {
            const value = valueAt(row, s.key);
            if (value === null) return "";
            const connected = index > 0 && valueAt(data[index - 1], s.key) !== null;
            return `${connected ? "L" : "M"}${x(index)},${y(value)}`;
          }).join(" ");
          return <g key={String(s.key)}><path d={path} fill="none" stroke={s.color} strokeWidth="2.5" strokeLinejoin="round" />
            {data.map((row, index) => { const value = valueAt(row, s.key); return value === null ? null : <circle key={index} cx={x(index)} cy={y(value)} r={selected === index ? 4 : 3} fill={s.color} stroke="white" strokeWidth="1.5" />; })}
          </g>;
        })}
        {current && <line x1={x(selected)} x2={x(selected)} y1="15" y2="177" stroke="#94a3b8" strokeDasharray="3 4" />}
        {data.map((row, index) => <g key={`${row.periodStart}-${row.periodEnd}-${row.period}`}>
          {(index === 0 || index === data.length - 1 || index === Math.floor((data.length - 1) / 2)) && <text x={x(index)} y="199" textAnchor={index === 0 ? "start" : index === data.length - 1 ? "end" : "middle"} fontSize="11" fill="#64748b">{row.periodEnd}</text>}
          <rect x={index === 0 ? 65 : (x(index - 1) + x(index)) / 2} y="10"
            width={(index === data.length - 1 ? 530 : (x(index) + x(index + 1)) / 2) - (index === 0 ? 65 : (x(index - 1) + x(index)) / 2)} height="172"
            fill="transparent" tabIndex={0} role="button" aria-label={`${label(row)}: ${available.map(s => `${s.label} ${formatted(valueAt(row, s.key), false)}`).join(", ")}`}
            onPointerEnter={() => setActive(index)} onClick={() => setActive(index)} onFocus={() => setActive(index)}
            onKeyDown={event => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); setActive(index); } }}>
            <title>{`${label(row)}: ${available.map(s => `${s.label} ${formatted(valueAt(row, s.key), false)}`).join(", ")}`}</title>
          </rect>
        </g>)}
      </svg>
      {current && <div className="mt-1 rounded-lg bg-slate-50 px-3 py-2 text-xs" aria-live="polite" aria-atomic="true">
        <p className="font-medium text-slate-500">{label(current)}</p>
        <dl className="mt-1 flex flex-wrap gap-x-4 gap-y-1">{available.map(s => <div key={String(s.key)} className="flex flex-wrap gap-1.5"><dt className="text-slate-600">{s.label}</dt><dd className="font-semibold tabular-nums">{formatted(valueAt(current, s.key), false)}</dd></div>)}</dl>
      </div>}
    </>}
    {missing.length > 0 && <p className="mt-3 text-xs text-slate-500">{missing.map(s => `${s.label} unavailable`).join(" · ")}</p>}
    {note && <p className="mt-2 text-xs leading-relaxed text-slate-500">{note}</p>}
  </section>;
}
