import type { PeriodData } from "@/lib/dashboard";
import { TrendChart } from "./trend-chart";

const purple = "#4f46e5";
const teal = "#0f766e";
const amber = "#b45309";

export function DashboardCharts({ data }: { data: PeriodData }) {
  return <div className="grid items-start gap-6 lg:grid-cols-2">
    <TrendChart title="Revenue & Net Income" data={data.income} series={[
      { key: "revenue", label: "Revenue", color: purple }, { key: "netIncome", label: "Net income", color: teal },
    ]} />
    <TrendChart title="Revenue Growth" data={data.growth} format="percent" series={[
      { key: "revenueGrowth", label: "Revenue growth", color: purple },
    ]} note="Year over year, using comparable reporting periods." />
    <TrendChart title="Profit Margins" data={data.profitability} format="percent" series={[
      { key: "grossMargin", label: "Gross margin", color: purple },
      { key: "operatingMargin", label: "Operating margin", color: teal },
      { key: "netMargin", label: "Net margin", color: amber },
    ]} />
    <TrendChart title="Free Cash Flow" data={data.cashFlow} series={[
      { key: "operatingCashFlow", label: "Operating cash flow", color: purple },
      { key: "capitalExpenditures", label: "CapEx", color: amber },
      { key: "freeCashFlow", label: "Free cash flow", color: teal },
    ]} note="CapEx is shown as positive cash spent. Free cash flow = operating cash flow − CapEx." />
    <TrendChart title="Cash vs Debt" data={data.balances} series={[
      { key: "cash", label: "Cash & equivalents", color: purple }, { key: "debt", label: "Total debt", color: amber },
    ]} note="Debt requires reported short-term borrowings and both current and noncurrent long-term debt. Missing components are not treated as zero." />
    <TrendChart title="Assets vs Liabilities" data={data.balances} series={[
      { key: "assets", label: "Total assets", color: purple }, { key: "liabilities", label: "Total liabilities", color: teal },
    ]} />
  </div>;
}
