package com.equitylens.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BalanceSheetResponse(
        String period,
        LocalDate periodEnd,
        BigDecimal cash,
        BigDecimal shortTermInvestments,
        BigDecimal currentAssets,
        BigDecimal assets,
        BigDecimal currentLiabilities,
        BigDecimal currentDebt,
        BigDecimal longTermDebt,
        BigDecimal stockholdersEquity,
        String unit
) {
}