package com.equitylens.service;

import com.equitylens.dto.CompanySummaryResponse;
import com.equitylens.entity.Company;
import com.equitylens.entity.FinancialMetric;
import com.equitylens.repository.CompanyRepository;
import com.equitylens.repository.FinancialMetricRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompanySummaryService {

    private final CompanyRepository companyRepository;
    private final FinancialMetricRepository financialMetricRepository;

    public CompanySummaryService(
            CompanyRepository companyRepository,
            FinancialMetricRepository financialMetricRepository
    ) {
        this.companyRepository = companyRepository;
        this.financialMetricRepository = financialMetricRepository;
    }

    public CompanySummaryResponse getSummary(String ticker) {

        Company company = companyRepository
                .findByTickerIgnoreCase(ticker)
                .orElseThrow(() ->
                        new RuntimeException("Company not found: " + ticker));

        List<FinancialMetric> metrics = financialMetricRepository
                .findAll()
                .stream()
                .filter(metric ->
                        metric.getCompany() != null
                                && metric.getCompany().getId().equals(company.getId()))
                .toList();

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