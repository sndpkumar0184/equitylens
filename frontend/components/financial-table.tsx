import type { CashFlow, DashboardBalance } from "@/lib/dashboard";
import { balanceAt, money, periodLabel, type IncomeStatement } from "@/lib/financials";

export function FinancialTable({ periods, balances, cashFlow }: { periods: IncomeStatement[]; balances: DashboardBalance[]; cashFlow: CashFlow[] }) {
  const columns = [...periods].reverse();
  const cashAt = (row: IncomeStatement) => cashFlow.find(c => c.periodStart === row.periodStart && c.periodEnd === row.periodEnd && c.unit === row.unit);
  const rows: { label: string; value: (row: IncomeStatement) => number | null | undefined }[] = [
    { label: "Revenue", value: row => row.revenue },
    { label: "Cost of revenue", value: row => row.costOfRevenue },
    { label: "Gross profit", value: row => row.grossProfit },
    { label: "Operating income", value: row => row.operatingIncome },
    { label: "Net income", value: row => row.netIncome },
    { label: "Operating cash flow", value: row => cashAt(row)?.operatingCashFlow },
    { label: "Capital expenditures", value: row => cashAt(row)?.capitalExpenditures },
    { label: "Free cash flow", value: row => cashAt(row)?.freeCashFlow },
    { label: "Cash & equivalents", value: row => balanceAt(balances, row.periodEnd)?.cash },
    { label: "Total debt", value: row => balances.find(b => b.periodEnd === row.periodEnd && b.unit === row.unit)?.debt },
    { label: "Total assets", value: row => balanceAt(balances, row.periodEnd)?.assets },
    { label: "Total liabilities", value: row => balances.find(b => b.periodEnd === row.periodEnd && b.unit === row.unit)?.liabilities },
  ];
  return <section className="panel overflow-hidden" aria-labelledby="financial-data-heading">
    <div className="border-b border-slate-200 p-5 sm:px-6"><h2 id="financial-data-heading" className="font-semibold">Financial data</h2><p className="mt-1 text-xs text-slate-500">Income statement, cash flow & balance sheet · USD · Latest first</p></div>
    {columns.length === 0 ? <p className="p-6 text-sm text-slate-500">No financial statements to display.</p> : <div className="overflow-x-auto" tabIndex={0} role="region" aria-label="Financial data table, scroll horizontally for more periods">
      <table className="w-full border-collapse whitespace-nowrap text-sm tabular-nums"><caption className="sr-only">Financial results in US dollars. Columns show reporting periods ending on the listed dates.</caption>
        <thead><tr className="bg-slate-50"><th scope="col" className="sticky left-0 z-10 bg-slate-50 px-6 py-4 text-left font-medium text-slate-500">Metric</th>{columns.map(row => <th scope="col" key={`${row.periodStart}-${row.periodEnd}`} className="px-6 py-4 text-right text-xs font-medium text-slate-500">{periodLabel(row)}</th>)}</tr></thead>
        <tbody>{rows.map(row => <tr key={row.label} className="border-t border-slate-100"><th scope="row" className="sticky left-0 bg-white px-6 py-4 text-left font-medium">{row.label}</th>{columns.map(period => <td key={`${period.periodStart}-${period.periodEnd}`} className="px-6 py-4 text-right text-slate-600">{money(row.value(period), false)}</td>)}</tr>)}</tbody>
      </table>
    </div>}
  </section>;
}
