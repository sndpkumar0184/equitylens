package com.equitylens.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import com.equitylens.providers.MarketDataProvider.Interval;

public record MarketHistoryResponse(String ticker, String source, String currency, String status,
        Instant fetchedAt, Instant retryAfter, String priceBasis,
        BigDecimal latestPrice, LocalDate latestTradingDate, BigDecimal dailyChange, BigDecimal dailyChangePercent,
        Long latestVolume, BigDecimal high52Week, BigDecimal low52Week,
        BigDecimal return1Month, BigDecimal return3Month, BigDecimal return6Month, BigDecimal return1Year,
        Interval interval, Set<Interval> supportedIntervals, List<DailyPrice> history) {}
