package com.equitylens.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalValuationResponse(
        String ticker,
        LocalDate periodEnd,

        BigDecimal sharePrice,
        BigDecimal sharesOutstanding,
        BigDecimal marketCap,

        BigDecimal cash,
        BigDecimal totalDebt,
        BigDecimal netDebt,
        BigDecimal enterpriseValue,

        BigDecimal ttmRevenue,
        BigDecimal ttmNetIncome,
        BigDecimal ttmFreeCashFlow,

        BigDecimal peRatio,
        BigDecimal priceToFreeCashFlow,
        BigDecimal evToFreeCashFlow
) {
}
