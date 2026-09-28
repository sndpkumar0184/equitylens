package com.equitylens.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Backend-selected observation. Period is Q1-Q4 (known fiscal quarter), QUARTER
 * (standalone quarter without a known fiscal number), YTD, FY, POINT_IN_TIME,
 * ROLLING_YEAR (a trailing year disclosed in a quarterly filing), or UNKNOWN (an unsupported duration). Dates and units retain SEC reporting boundaries.
 */
public record NormalizedFinancialMetricResponse(
        String metric,
        String period,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal value,
        String unit
) {
}