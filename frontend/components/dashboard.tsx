"use client";

import { useState } from "react";
import { balanceAt, dateLabel, money, selectPeriods, type Company, type IncomeStatement, type BalanceSheet, type Frequency } from "@/lib/financials";
import { TrendChart } from "./trend-chart";
import { FinancialTable } from "./financial-table";

export function Dashboard({ company, income, balances }: { company: Company; income: IncomeStatement[]; balances: BalanceSheet[] }) {
  const [frequency, setFrequency] = useState<Frequency>("annual");
  const periods = selectPeriods(income, frequency).slice(-8);
  const latest = periods.at(-1);
  const balance = balanceAt(balances, latest?.periodEnd);
  const cards = [
    { label: "Revenue", value: latest?.revenue, note: "Total revenue for the period" },
    { label: "Net income", value: latest?.netIncome, note: "Net earnings for the period" },
    { label: "Cash", value: balance?.cash, note: "Cash & equivalents at period end" },
    { label: "Assets", value: balance?.assets, note: "Total assets at period end" },
  ];
  return <div className="space-y-7">
    <section className="flex flex-col justify-between gap-6 border-b border-slate-200 pb-7 sm:flex-row sm:items-center">
      <div className="flex items-start gap-4">
        <div aria-hidden="true" className="hidden h-16 w-16 shrink-0 items-center justify-center rounded-2xl bg-slate-900 text-xl font-bold text-white sm:flex">{company.ticker.slice(0, 2)}</div>
        <div><div className="mb-2 flex items-center gap-3"><span className="rounded-md bg-indigo-50 px-2.5 py-1 text-xs font-bold tracking-wide text-indigo-700">{company.ticker}</span><span className="eyebrow">Company research</span></div>
          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{company.name}</h1>
          <p className="mt-2 text-sm text-slate-500">{company.sector || "Sector unavailable"}<span className="mx-2">/</span>{company.industry || "Industry unavailable"}</p>
        </div>
      </div>
      <div className="text-sm text-slate-500 sm:text-right"><p className="font-medium text-slate-700">Financial overview</p><p className="mt-1">SEC company financials</p></div>
    </section>
    <section aria-labelledby="overview-heading">
      <div className="mb-5 flex flex-wrap items-center justify-between gap-4">
        <div><h2 id="overview-heading" className="text-lg font-semibold">At a glance</h2><p className="mt-1 text-xs text-slate-500">{latest ? `${frequency === "annual" ? "Annual" : "Quarterly"} period ended ${dateLabel(latest.periodEnd)} · USD` : "No matching reporting periods"}</p></div>
        <div aria-label="Reporting frequency" className="inline-flex rounded-lg border border-slate-200 bg-white p-1">{(["annual", "quarterly"] as const).map(value => <button key={value} aria-pressed={frequency === value} onClick={() => setFrequency(value)} className={`rounded-md px-4 py-2 text-sm font-medium transition-colors ${frequency === value ? "bg-slate-900 text-white" : "text-slate-500 hover:bg-slate-100"}`}>{value === "annual" ? "Annual" : "Quarterly"}</button>)}</div>
      </div>
      {!latest && <p role="status" className="mb-5 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">No {frequency} USD statements are available for this company. Try another reporting frequency or ticker.</p>}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{cards.map(card => <article key={card.label} className="panel p-5"><h3 className="text-sm font-medium text-slate-500">{card.label}</h3><p className="mt-3 text-3xl font-semibold tracking-tight tabular-nums" title={money(card.value, false)}>{money(card.value)}</p><p className="mt-3 text-xs text-slate-500">{card.note}</p></article>)}</div>
    </section>
    <div className="grid gap-6 lg:grid-cols-2"><TrendChart title="Revenue" metric="revenue" periods={periods} /><TrendChart title="Net income" metric="netIncome" periods={periods} /></div>
    <FinancialTable periods={periods} balances={balances} />
    <p className="pb-4 text-xs leading-relaxed text-slate-500">Source: SEC filings via EquityLens. Values are shown in USD. Cash and assets reflect balances at the reporting period’s end. A dash indicates unavailable data.</p>
  </div>;
}
