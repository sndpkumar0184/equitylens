package com.equitylens.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** All charts and the table share these reporting-period boundaries; percentages are percentage points. */
public record DashboardResponse(CompanyResponse company, PeriodData annual, PeriodData quarterly) {
    public record PeriodData(Summary summary, List<IncomeStatementResponse> income,
            List<Growth> growth, List<Profitability> profitability,
            List<CashFlowResponse> cashFlow, List<Balance> balances) {}

    public record MetricValue(BigDecimal value, String period, LocalDate periodStart, LocalDate periodEnd) {}
    public record Summary(MetricValue revenue, MetricValue netIncome, MetricValue freeCashFlow,
            MetricValue cash, MetricValue totalAssets, MetricValue totalLiabilities) {}
    public record Growth(String period, LocalDate periodStart, LocalDate periodEnd, BigDecimal revenueGrowth) {}
    public record Profitability(String period, LocalDate periodStart, LocalDate periodEnd,
            BigDecimal grossMargin, BigDecimal operatingMargin, BigDecimal netMargin) {}
    public record Balance(String period, LocalDate periodEnd, String unit,
            BigDecimal cash, BigDecimal debt, BigDecimal assets, BigDecimal liabilities) {}
}
