package com.equitylens.service;

import com.equitylens.dto.CompanySummaryResponse;
import com.equitylens.entity.Company;
import com.equitylens.entity.FinancialMetric;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompanySummaryService {

    private final CompanyService companyService;
    private final FinancialDataService financialDataService;

    public CompanySummaryService(CompanyService companyService, FinancialDataService financialDataService) {
        this.companyService = companyService;
        this.financialDataService = financialDataService;
    }

    public CompanySummaryResponse getSummary(String ticker) {
        Company company = companyService.getCompany(ticker);
        List<FinancialMetric> metrics = financialDataService.getCompanyFinancials(ticker);

        if (metrics.isEmpty()) {
            throw new RuntimeException(
                    "No financial data found for company: " + ticker);
        }

        LocalDate latestPeriod = metrics.stream()
                .map(FinancialMetric::getPeriodEnd)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .orElseThrow(() ->
                        new RuntimeException(
                                "No financial period found for company: " + ticker));

        Map<String, FinancialMetric> latestMetrics = metrics.stream()
                .filter(metric ->
                        latestPeriod.equals(metric.getPeriodEnd()))
                .collect(Collectors.toMap(
                        FinancialMetric::getMetric,
                        Function.identity(),
                        (first, second) -> first
                ));

        BigDecimal revenue = getValue(latestMetrics, "revenue");
        BigDecimal netIncome = getValue(latestMetrics, "net_income");
        BigDecimal cash = getValue(latestMetrics, "cash");
        BigDecimal assets = getValue(latestMetrics, "assets");

        return new CompanySummaryResponse(
                company.getTicker(),
                company.getName(),
                company.getSector(),
                company.getIndustry(),
                revenue,
                netIncome,
                cash,
                assets,
                latestPeriod
        );
    }

    private BigDecimal getValue(
            Map<String, FinancialMetric> metrics,
            String metricName
    ) {
        FinancialMetric metric = metrics.get(metricName);

        return metric == null ? null : metric.getValue();
    }
}