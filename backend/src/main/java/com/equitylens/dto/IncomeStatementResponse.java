package com.equitylens.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IncomeStatementResponse(
        String period,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal revenue,
        BigDecimal costOfRevenue,
        BigDecimal grossProfit,
        BigDecimal operatingIncome,
        BigDecimal netIncome,
        String unit
) {
}