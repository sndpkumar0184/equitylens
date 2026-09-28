package com.equitylens.service;

import com.equitylens.dto.DailyPrice;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MarketAnalyticsTest {
    private DailyPrice price(String date, String close) {
        return new DailyPrice(LocalDate.parse(date), null, new BigDecimal(close).add(BigDecimal.TEN),
                new BigDecimal(close).subtract(BigDecimal.TEN), new BigDecimal(close), null, null);
    }
    private void number(String expected, BigDecimal actual) {
        assertNotNull(actual); assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    @Test void calculatesAllFourReturnsFromTheirOwnCalendarAnchors() {
        var latest = price("2026-09-25", "120");
        var rows = List.of(price("2025-09-25", "80"), price("2026-03-25", "100"),
                price("2026-06-25", "150"), price("2026-08-25", "120"), latest);
        number("0", MarketAnalytics.priceReturn(rows, latest, 1));
        number("-20", MarketAnalytics.priceReturn(rows, latest, 3));
        number("20", MarketAnalytics.priceReturn(rows, latest, 6));
        number("50", MarketAnalytics.priceReturn(rows, latest, 12));
    }

    @Test void weekendUsesFridayAndNeverLooksAheadToMonday() {
        var latest = price("2026-03-30", "120"); // one month ago = Saturday Feb 28
        var rows = List.of(price("2026-02-27", "100"), price("2026-03-02", "200"), latest);
        number("20", MarketAnalytics.priceReturn(rows, latest, 1));
    }

    @Test void holidayUsesPrecedingAvailableSession() {
        var latest = price("2026-08-03", "120"); // July 3 holiday, preceding session July 2
        number("20", MarketAnalytics.priceReturn(List.of(price("2026-07-02", "100"), latest), latest, 1));
    }

    @Test void missingOrStaleAnchorAndZeroDenominatorReturnNull() {
        var latest = price("2026-09-25", "120");
        for (var rows : List.of(List.of(latest), List.of(price("2026-08-01", "100"), latest),
                List.of(price("2026-08-25", "0"), latest)))
            assertNull(MarketAnalytics.priceReturn(rows, latest, 1));
        assertNull(MarketAnalytics.priceReturn(List.of(), null, 1));
    }

    @Test void fiftyTwoWeekExtremesUseHighAndLowNotCloseAndExcludeOlderPrices() {
        var latest = price("2026-09-25", "120");
        var rows = List.of(price("2025-09-01", "999"), price("2025-09-26", "100"),
                price("2026-01-02", "50"), price("2026-07-02", "150"), latest);
        number("160", MarketAnalytics.extreme(rows, latest, true));
        number("40", MarketAnalytics.extreme(rows, latest, false));
    }

    @Test void shortHistoryAndMissingHighOrLowStayUnavailable() {
        var latest = price("2026-09-25", "120");
        assertNull(MarketAnalytics.extreme(List.of(latest), latest, true));
        var missing = new DailyPrice(LocalDate.parse("2026-05-01"), null, null, null, BigDecimal.TEN, null, null);
        var rows = List.of(price("2025-09-26", "100"), missing, latest);
        assertNull(MarketAnalytics.extreme(rows, latest, true));
        assertNull(MarketAnalytics.extreme(rows, latest, false));
        assertNull(MarketAnalytics.extreme(List.of(), null, true));
    }
}
