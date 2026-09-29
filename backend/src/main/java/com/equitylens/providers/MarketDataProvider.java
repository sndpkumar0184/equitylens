package com.equitylens.providers;

import com.equitylens.dto.DailyPrice;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Provider-neutral contract for historical market observations used by EquityLens. */
public interface MarketDataProvider {
    String source();
    List<DailyPrice> getHistory(String ticker, LocalDate from, LocalDate to);
    Set<Interval> supportedIntervals();

    enum Interval { HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY }

    class Unavailable extends RuntimeException {
        private final String status;
        private final long retrySeconds;
        public Unavailable(String status, long retrySeconds) {
            super(status); this.status = status; this.retrySeconds = retrySeconds;
        }
        public String status() { return status; }
        public long retrySeconds() { return retrySeconds; }
    }
}
