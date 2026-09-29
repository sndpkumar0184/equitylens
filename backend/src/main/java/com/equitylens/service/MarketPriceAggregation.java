package com.equitylens.service;

import com.equitylens.dto.DailyPrice;
import com.equitylens.providers.MarketDataProvider.Interval;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Aggregates actual daily observations; it never synthesizes finer-grained bars. */
final class MarketPriceAggregation {
    private MarketPriceAggregation() {}

    static List<DailyPrice> aggregate(List<DailyPrice> input, Interval interval) {
        if (interval == Interval.DAILY) return List.copyOf(input);
        if (interval != Interval.WEEKLY && interval != Interval.MONTHLY && interval != Interval.YEARLY) throw new IllegalArgumentException("Unsupported aggregation interval");
        Map<LocalDate, List<DailyPrice>> groups = new TreeMap<>();
        for (DailyPrice row : input) {
            LocalDate key = switch (interval) {
                case WEEKLY -> row.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MONTHLY -> row.date().withDayOfMonth(1);
                case YEARLY -> row.date().withDayOfYear(1);
                default -> throw new IllegalArgumentException("Unsupported aggregation interval");
            };
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<DailyPrice> result = new ArrayList<>();
        for (var group : groups.entrySet()) {
            List<DailyPrice> bars = group.getValue(); // database/provider ordering is chronological
            DailyPrice first = bars.getFirst(), last = bars.getLast();
            BigDecimal high = bars.stream().anyMatch(v -> v.high() == null) ? null
                    : bars.stream().map(DailyPrice::high).max(BigDecimal::compareTo).orElse(null);
            BigDecimal low = bars.stream().anyMatch(v -> v.low() == null) ? null
                    : bars.stream().map(DailyPrice::low).min(BigDecimal::compareTo).orElse(null);
            Long volume = bars.stream().anyMatch(v -> v.volume() == null) ? null
                    : bars.stream().mapToLong(DailyPrice::volume).sum();
            result.add(new DailyPrice(group.getKey(), first.open(), high, low, last.close(), null, volume));
        }
        return List.copyOf(result);
    }
}
