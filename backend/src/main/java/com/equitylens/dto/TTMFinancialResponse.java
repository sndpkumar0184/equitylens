package com.equitylens.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TTMFinancialResponse(

        String period,

        LocalDate periodEnd,

        BigDecimal revenue,

        BigDecimal costOfRevenue,

        BigDecimal grossProfit,

        BigDecimal operatingIncome,

        BigDecimal netIncome,

        BigDecimal operatingCashFlow,

        BigDecimal capitalExpenditures,

        BigDecimal investingCashFlow,

        BigDecimal financingCashFlow,

        BigDecimal freeCashFlow,

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

