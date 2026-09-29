package com.equitylens.providers;

import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class StashGammaMarketDataProviderTest {
    private final LocalDate from = LocalDate.of(2026, 7, 1), to = from.plusDays(7);
    private StashGammaMarketDataProvider provider() {
        return new StashGammaMarketDataProvider(RestClient.builder(), new ObjectMapper(), "https://provider.test", "test-key");
    }
    private String payload(String bars) { return "{\"symbol\":\"AAPL\",\"bars\":[" + bars + "]}"; }
    private final String row = """
        {"date":"2026-07-02","open":100.12,"high":110.5,"low":99.8,"close":105.25,"volume":500}
        """;

    @Test void parsesDecimalsSortsDatesAndCollapsesIdenticalDuplicates() {
        var rows = provider().parse(payload(row + "," + row + "," + row.replace("07-02", "07-01")), "AAPL", from, to);
        assertEquals(2, rows.size()); assertEquals(from, rows.getFirst().date());
        assertEquals("105.25", rows.getFirst().close().toPlainString());
        assertNull(rows.getFirst().adjustedClose()); assertEquals(500L, rows.getFirst().volume());
    }

    @Test void rejectsMismatchedSymbolMalformedJsonAndConflictingDuplicates() {
        for (String json : new String[]{"<html>error</html>", payload(row).replace("AAPL", "META"),
                payload(row + "," + row.replace("105.25", "106.25")), "{\"symbol\":\"AAPL\"}"})
            assertThrows(DailyPriceProvider.Unavailable.class, () -> provider().parse(json, "AAPL", from, to));
    }

    @Test void rejectsInvalidCloseVolumeBoundsAndDates() {
        for (String json : new String[]{payload(row.replace("105.25", "null")), payload(row.replace("105.25", "0")),
                payload(row.replace("500", "-1")), payload(row.replace("500", "0.5")),
                payload(row.replace("110.5", "100")), payload(row.replace("07-02", "08-02"))})
            assertThrows(DailyPriceProvider.Unavailable.class, () -> provider().parse(json, "AAPL", from, to));
    }

    @Test void emptyAndOptionalMissingFieldsAreSafe() {
        assertTrue(provider().parse(payload(""), "AAPL", from, to).isEmpty());
        var rows = provider().parse(payload("{\"date\":\"2026-07-01\",\"close\":10}"), "AAPL", from, to);
        assertNull(rows.getFirst().open()); assertNull(rows.getFirst().volume());
    }

    @Test void missingKeyDoesNotCallNetwork() {
        var provider = new StashGammaMarketDataProvider(RestClient.builder(), new ObjectMapper(), "https://provider.test", "");
        assertEquals("NOT_CONFIGURED", assertThrows(DailyPriceProvider.Unavailable.class,
                () -> provider.getHistory("AAPL", from, to)).status());
    }

    @Test void usesDocumentedHeaderAndPathAndHonors429WithoutAnotherRequest() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new AtomicInteger();
        server.createContext("/eod/AAPL", exchange -> {
            calls.incrementAndGet();
            boolean valid = "test-key".equals(exchange.getRequestHeaders().getFirst("X-Api-Key"))
                    && "from=2026-07-01&to=2026-07-08".equals(exchange.getRequestURI().getQuery());
            exchange.getResponseHeaders().add("Retry-After", "120");
            exchange.sendResponseHeaders(valid ? 429 : 400, -1); exchange.close();
        });
        server.start();
        try {
            var provider = new StashGammaMarketDataProvider(RestClient.builder(), new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
            var error = assertThrows(DailyPriceProvider.Unavailable.class, () -> provider.getHistory("aapl", from, to));
            assertEquals("RATE_LIMITED", error.status()); assertEquals(120, error.retrySeconds());
            assertThrows(DailyPriceProvider.Unavailable.class, () -> provider.getHistory("META", from, to));
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }

    @Test void exposesDailyAndAggregatedIntervalsButNotHourly() {
        assertEquals(java.util.Set.of(MarketDataProvider.Interval.DAILY), provider().supportedIntervals());
        assertFalse(provider().supportedIntervals().contains(MarketDataProvider.Interval.HOURLY));
    }
}
