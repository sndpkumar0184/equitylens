package com.equitylens.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketDataResponse(
        String ticker,
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal sharesOutstanding,
        BigDecimal marketCap,
        BigDecimal fiftyTwoWeekHigh,
        BigDecimal fiftyTwoWeekLow,
        BigDecimal beta,
        LocalDateTime marketDataTimestamp
) {
}