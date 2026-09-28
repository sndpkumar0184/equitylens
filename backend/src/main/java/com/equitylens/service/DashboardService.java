package com.equitylens.service;

import com.equitylens.dto.*;
import com.equitylens.dto.DashboardResponse.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    static final Set<String> METRICS = Set.of("revenue", "net_income", "cost_of_revenue", "gross_profit",
            "operating_income", "operating_cash_flow", "capital_expenditures", "investing_cash_flow",
            "financing_cash_flow", "cash", "assets", "liabilities", "current_debt", "long_term_debt", "short_term_borrowings");
    private static final int DISPLAY_PERIODS = 8;
    private final CompanyService companies;
    private final FinancialDataService financials;
    private final FinancialMetricNormalizer normalizer;

    public DashboardService(CompanyService companies, FinancialDataService financials, FinancialMetricNormalizer normalizer) {
        this.companies = companies;
        this.financials = financials;
        this.normalizer = normalizer;
    }

    public DashboardResponse getDashboard(String ticker) {
        var company = companies.getCompany(ticker);
        var normalized = normalizer.normalize(financials.getDashboardFinancials(company.getTicker(), METRICS));
        return new DashboardResponse(CompanyResponse.from(company), build(normalized, true), build(normalized, false));
    }

    PeriodData build(List<NormalizedFinancialMetricResponse> normalized, boolean annual) {
        // One normalization pass upstream. Never join different currencies, durations or reporting dates.
        Map<PeriodKey, List<NormalizedFinancialMetricResponse>> flows = normalized.stream()
                .filter(m -> "USD".equals(m.unit()))
                .filter(m -> annual ? "FY".equals(m.period()) : m.period().matches("Q[1-4]|QUARTER"))
                .collect(Collectors.groupingBy(m -> new PeriodKey(m.period(), m.periodStart(), m.periodEnd())));
        List<PeriodKey> periods = flows.keySet().stream().sorted(Comparator.comparing(PeriodKey::end)
                .thenComparing(PeriodKey::start).thenComparing(PeriodKey::period)).toList();
        List<IncomeStatementResponse> history = periods.stream().map(key -> {
            var values = values(flows.get(key));
            return new IncomeStatementResponse(key.period(), key.start(), key.end(), values.get("revenue"),
                    values.get("cost_of_revenue"), values.get("gross_profit"), values.get("operating_income"),
                    values.get("net_income"), "USD");
        }).toList();
        List<PeriodKey> shown = periods.subList(Math.max(0, periods.size() - DISPLAY_PERIODS), periods.size());
        List<IncomeStatementResponse> income = history.subList(Math.max(0, history.size() - DISPLAY_PERIODS), history.size());
        List<Growth> growth = income.stream().map(row -> new Growth(row.period(), row.periodStart(), row.periodEnd(),
                DashboardCalculations.growth(row, history))).toList();
        List<Profitability> profitability = income.stream().map(row -> new Profitability(row.period(), row.periodStart(), row.periodEnd(),
                DashboardCalculations.margin(row.grossProfit(), row.revenue()),
                DashboardCalculations.margin(row.operatingIncome(), row.revenue()),
                DashboardCalculations.margin(row.netIncome(), row.revenue()))).toList();
        List<CashFlowResponse> cashFlow = shown.stream().map(key -> {
            var values = values(flows.get(key));
            var operating = values.get("operating_cash_flow");
            var capex = DashboardCalculations.capexSpend(values.get("capital_expenditures"));
            return new CashFlowResponse(key.period(), key.start(), key.end(), operating, capex,
                    values.get("investing_cash_flow"), values.get("financing_cash_flow"),
                    DashboardCalculations.freeCashFlow(operating, capex), "USD");
        }).toList();
        Map<LocalDate, List<NormalizedFinancialMetricResponse>> points = normalized.stream()
                .filter(m -> "USD".equals(m.unit()) && "POINT_IN_TIME".equals(m.period()))
                .collect(Collectors.groupingBy(NormalizedFinancialMetricResponse::periodEnd));
        List<Balance> balances = shown.stream().map(key -> {
            var values = values(points.getOrDefault(key.end(), List.of()));
            return new Balance(key.period(), key.end(), "USD", values.get("cash"),
                    DashboardCalculations.debt(values.get("short_term_borrowings"), values.get("current_debt"), values.get("long_term_debt")),
                    values.get("assets"), values.get("liabilities"));
        }).toList();
        Summary summary = new Summary(
                latest(income, IncomeStatementResponse::revenue, IncomeStatementResponse::period, IncomeStatementResponse::periodStart, IncomeStatementResponse::periodEnd),
                latest(income, IncomeStatementResponse::netIncome, IncomeStatementResponse::period, IncomeStatementResponse::periodStart, IncomeStatementResponse::periodEnd),
                latest(cashFlow, CashFlowResponse::freeCashFlow, CashFlowResponse::period, CashFlowResponse::periodStart, CashFlowResponse::periodEnd),
                latest(balances, Balance::cash, Balance::period, row -> null, Balance::periodEnd),
                latest(balances, Balance::assets, Balance::period, row -> null, Balance::periodEnd),
                latest(balances, Balance::liabilities, Balance::period, row -> null, Balance::periodEnd));
        return new PeriodData(summary, income, growth, profitability, cashFlow, balances);
    }

    private Map<String, BigDecimal> values(List<NormalizedFinancialMetricResponse> facts) {
        return facts.stream().filter(f -> f.value() != null).collect(Collectors.toMap(
                NormalizedFinancialMetricResponse::metric, NormalizedFinancialMetricResponse::value));
    }

    private <T> MetricValue latest(List<T> rows, Function<T, BigDecimal> value,
            Function<T, String> period, Function<T, LocalDate> start, Function<T, LocalDate> end) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            T row = rows.get(i);
            if (value.apply(row) != null) return new MetricValue(value.apply(row), period.apply(row), start.apply(row), end.apply(row));
        }
        return new MetricValue(null, null, null, null);
    }

    private record PeriodKey(String period, LocalDate start, LocalDate end) {}
}
