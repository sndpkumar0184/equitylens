package com.equitylens.providers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface MarketDataProvider {

    MarketQuote getQuote(String ticker);

    record MarketQuote(
            BigDecimal currentPrice,
            BigDecimal previousClose,
            BigDecimal sharesOutstanding,
            BigDecimal marketCap,
            BigDecimal fiftyTwoWeekHigh,
            BigDecimal fiftyTwoWeekLow,
            BigDecimal beta,
            LocalDateTime timestamp
    ) {
    }
}