package com.equitylens.service;

import com.equitylens.dto.DailyPrice;
import com.equitylens.providers.MarketDataProvider.Interval;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MarketPriceAggregationTest {
    private DailyPrice bar(String date, String open, String high, String low, String close, long volume) {
        return new DailyPrice(LocalDate.parse(date), new BigDecimal(open), new BigDecimal(high), new BigDecimal(low), new BigDecimal(close), null, volume);
    }

    @Test void monthlyAggregationUsesFirstOpenExtremesLastCloseAndVolumeSum() {
        var bars = List.of(bar("2026-01-30", "10", "14", "9", "12", 100), bar("2026-02-02", "12", "16", "11", "15", 150), bar("2026-02-27", "15", "17", "13", "16", 250));
        var result = MarketPriceAggregation.aggregate(bars, Interval.MONTHLY);
        assertEquals(2, result.size());
        assertEquals(LocalDate.parse("2026-02-01"), result.get(1).date());
        assertEquals(new BigDecimal("12"), result.get(1).open());
        assertEquals(new BigDecimal("17"), result.get(1).high());
        assertEquals(new BigDecimal("11"), result.get(1).low());
        assertEquals(new BigDecimal("16"), result.get(1).close());
        assertEquals(400L, result.get(1).volume());
    }

    @Test void yearlyAggregationStaysChronologicalAndMissingVolumeStaysUnknown() {
        var bars = List.of(bar("2025-12-31", "1", "2", "1", "2", 10),
                new DailyPrice(LocalDate.parse("2026-01-02"), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, null, null));
        var result = MarketPriceAggregation.aggregate(bars, Interval.YEARLY);
        assertEquals(LocalDate.parse("2025-01-01"), result.getFirst().date());
        assertEquals(LocalDate.parse("2026-01-01"), result.getLast().date());
        assertNull(result.getLast().volume());
        assertEquals(List.of(LocalDate.parse("2025-01-01"), LocalDate.parse("2026-01-01")), result.stream().map(DailyPrice::date).toList());
    }

    @Test void weeklyAggregationGroupsRealSessionsByMondayAndDoesNotInventWeekendBars() {
        var bars = List.of(bar("2026-09-21", "20", "23", "19", "22", 10),
                bar("2026-09-25", "22", "25", "21", "24", 20), bar("2026-09-28", "24", "27", "23", "26", 30));
        var weekly = MarketPriceAggregation.aggregate(bars, Interval.WEEKLY);
        assertEquals(List.of(LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-28")), weekly.stream().map(DailyPrice::date).toList());
        assertEquals(new BigDecimal("20"), weekly.getFirst().open());
        assertEquals(new BigDecimal("25"), weekly.getFirst().high());
        assertEquals(new BigDecimal("19"), weekly.getFirst().low());
        assertEquals(new BigDecimal("24"), weekly.getFirst().close());
        assertEquals(30L, weekly.getFirst().volume());
    }
}
