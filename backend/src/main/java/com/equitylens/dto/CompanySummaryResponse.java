package com.equitylens.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CompanySummaryResponse(
        String ticker,
        String name,
        String sector,
        String industry,
        BigDecimal latestRevenue,
        BigDecimal latestNetIncome,
        BigDecimal latestCash,
        BigDecimal latestAssets,
        LocalDate latestPeriod
) {
}