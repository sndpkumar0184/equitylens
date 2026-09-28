package com.equitylens.providers;

import com.equitylens.dto.DailyPrice;
import java.time.LocalDate;
import java.util.List;

/** Historical prices are separate from the existing Finnhub quote/valuation contract. */
public interface DailyPriceProvider {
    String source();
    List<DailyPrice> getHistory(String ticker, LocalDate from, LocalDate to);

    class Unavailable extends RuntimeException {
        private final String status;
        private final long retrySeconds;
        public Unavailable(String status, long retrySeconds) {
            super(status);
            this.status = status;
            this.retrySeconds = retrySeconds;
        }
        public String status() { return status; }
        public long retrySeconds() { return retrySeconds; }
    }
}
