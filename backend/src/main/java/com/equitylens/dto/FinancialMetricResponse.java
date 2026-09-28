package com.equitylens.dto;

import com.equitylens.entity.FinancialMetric;
import java.math.BigDecimal;
import java.time.LocalDate;

// Preserve the existing raw-financials shape, including provenance, without serializing JPA proxies.
public record FinancialMetricResponse(Long id, CompanyResponse company, String metric, LocalDate periodStart,
        LocalDate periodEnd, BigDecimal value, String unit, LocalDate filingDate, String form, String frame) {
    public static FinancialMetricResponse from(FinancialMetric metric) {
        return new FinancialMetricResponse(metric.getId(), CompanyResponse.from(metric.getCompany()), metric.getMetric(),
                metric.getPeriodStart(), metric.getPeriodEnd(), metric.getValue(), metric.getUnit(),
                metric.getFilingDate(), metric.getForm(), metric.getFrame());
    }
}
