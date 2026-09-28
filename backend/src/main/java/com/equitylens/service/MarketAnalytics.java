package com.equitylens.service;

import com.equitylens.dto.DailyPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

final class MarketAnalytics {
    private MarketAnalytics() {}

    static BigDecimal priceReturn(List<DailyPrice> rows, DailyPrice latest, int months) {
        if (latest == null || latest.close() == null) return null;
        LocalDate target = latest.date().minusMonths(months);
        DailyPrice prior = onOrBefore(rows, target);
        if (prior == null || prior.close() == null || prior.close().signum() <= 0) return null;
        return latest.close().subtract(prior.close()).multiply(BigDecimal.valueOf(100))
                .divide(prior.close(), 4, RoundingMode.HALF_UP);
    }

    // Use the preceding session (never look ahead), but don't substitute arbitrarily stale observations.
    static DailyPrice onOrBefore(List<DailyPrice> rows, LocalDate target) {
        return rows.stream().filter(p -> !p.date().isAfter(target) && !p.date().isBefore(target.minusDays(7)))
                .max(Comparator.comparing(DailyPrice::date)).orElse(null);
    }

    static BigDecimal extreme(List<DailyPrice> rows, DailyPrice latest, boolean high) {
        if (latest == null) return null;
        LocalDate start = latest.date().minusWeeks(52);
        // An IPO/short history must not be presented as a full 52-week range.
        if (onOrBefore(rows, start) == null) return null;
        var window = rows.stream().filter(p -> p.date().isAfter(start) && !p.date().isAfter(latest.date()))
                .map(p -> high ? p.high() : p.low()).toList();
        if (window.isEmpty() || window.stream().anyMatch(v -> v == null)) return null;
        return high ? window.stream().max(BigDecimal::compareTo).orElse(null)
                : window.stream().min(BigDecimal::compareTo).orElse(null);
    }
}
