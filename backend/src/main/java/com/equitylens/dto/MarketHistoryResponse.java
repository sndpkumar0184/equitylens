package com.equitylens.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MarketHistoryResponse(String ticker, String source, String currency, String status,
        Instant fetchedAt, Instant retryAfter, String priceBasis,
        BigDecimal latestPrice, LocalDate latestTradingDate, BigDecimal high52Week, BigDecimal low52Week,
        BigDecimal return1Month, BigDecimal return3Month, BigDecimal return6Month, BigDecimal return1Year,
        List<DailyPrice> history) {}
