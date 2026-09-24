package com.equitylens.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TTMRatiosResponse(
        String period,
        LocalDate periodEnd,

        BigDecimal grossMargin,
        BigDecimal operatingMargin,
        BigDecimal netMargin,
        BigDecimal fcfMargin,

        BigDecimal returnOnAssets,
        BigDecimal returnOnEquity,

        BigDecimal currentRatio,
        BigDecimal debtToEquity,
        BigDecimal debtToAssets,

        BigDecimal netDebt
) {}