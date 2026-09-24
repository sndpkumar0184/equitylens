package com.equitylens.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashFlowResponse(
        String period,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal operatingCashFlow,
        BigDecimal capitalExpenditures,
        BigDecimal investingCashFlow,
        BigDecimal financingCashFlow,
        BigDecimal freeCashFlow,
        String unit
) {
}