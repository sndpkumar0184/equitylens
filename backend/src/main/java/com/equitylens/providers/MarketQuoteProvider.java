package com.equitylens.providers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Legacy quote and valuation snapshot contract, separate from historical price bars. */
public interface MarketQuoteProvider {
    MarketQuote getQuote(String ticker);
    record MarketQuote(BigDecimal currentPrice, BigDecimal previousClose, BigDecimal sharesOutstanding,
            BigDecimal marketCap, BigDecimal fiftyTwoWeekHigh, BigDecimal fiftyTwoWeekLow,
            BigDecimal beta, LocalDateTime timestamp) {}
}
