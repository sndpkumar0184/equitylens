package com.equitylens.providers;

import com.equitylens.dto.DailyPrice;
import com.equitylens.service.Ticker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** StashGamma's authentication, HTTP protocol, response parsing, and quota policy live here. */
public class StashGammaMarketDataProvider implements DailyPriceProvider {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    // Per-process quota protection. Server-side limits remain authoritative across instances.
    private final Deque<Instant> calls = new ArrayDeque<>();
    private Instant blockedUntil = Instant.EPOCH;

    public StashGammaMarketDataProvider(RestClient.Builder builder, ObjectMapper mapper,
            @Value("${market.stashgamma.base-url:https://www.stashgamma.com/api/dataapi/v1}") String baseUrl,
            @Value("${market.stashgamma.api-key:}") String apiKey) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    public String source() { return "STASHGAMMA"; }

    @Override
    public Set<Interval> supportedIntervals() { return Set.of(Interval.DAILY); }

    @Override
    public synchronized List<DailyPrice> getHistory(String input, LocalDate from, LocalDate to) {
        String ticker = Ticker.normalize(input);
        if (apiKey.isBlank()) throw new Unavailable("NOT_CONFIGURED", 0);
        Instant now = Instant.now();
        if (now.isBefore(blockedUntil)) throw new Unavailable("RATE_LIMITED", Duration.between(now, blockedUntil).toSeconds() + 1);
        while (!calls.isEmpty() && calls.peekFirst().isBefore(now.minus(Duration.ofDays(7)))) calls.removeFirst();
        for (var window : Map.of(Duration.ofHours(1), 300, Duration.ofDays(1), 800, Duration.ofDays(7), 4000).entrySet()) {
            var inWindow = calls.stream().filter(t -> t.isAfter(now.minus(window.getKey()))).toList();
            if (inWindow.size() >= window.getValue()) {
                blockedUntil = inWindow.getFirst().plus(window.getKey());
                throw new Unavailable("RATE_LIMITED", Duration.between(now, blockedUntil).toSeconds() + 1);
            }
        }
        calls.addLast(now);
        try {
            String body = client.get().uri(b -> b.path("/eod/{symbol}")
                    .queryParam("from", from).queryParam("to", to).build(ticker))
                    .header("X-Api-Key", apiKey).retrieve().body(String.class);
            return parse(body, ticker, from, to);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return List.of();
            long retry = ex.getStatusCode().value() == 429 ? retrySeconds(ex) : 900;
            blockedUntil = Instant.now().plusSeconds(retry);
            // Never propagate provider bodies/headers or credentials to callers/logs.
            throw new Unavailable(ex.getStatusCode().value() == 429 ? "RATE_LIMITED" : "PROVIDER_ERROR", retry);
        } catch (Unavailable ex) {
            throw ex;
        } catch (Exception ex) {
            throw new Unavailable("PROVIDER_ERROR", 900);
        }
    }

    private long retrySeconds(RestClientResponseException ex) {
        String value = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Retry-After");
        try { return Math.max(1, Long.parseLong(value)); }
        catch (Exception ignored) {
            try { return Math.max(1, Duration.between(Instant.now(), ZonedDateTime.parse(value,
                    DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds()); }
            catch (Exception invalid) { return 3600; }
        }
    }

    List<DailyPrice> parse(String body, String ticker, LocalDate from, LocalDate to) {
        try {
            JsonNode root = mapper.readTree(body);
            if (!ticker.equalsIgnoreCase(root.path("symbol").asString()) || !root.path("bars").isArray())
                throw new IllegalArgumentException("Unexpected response");
            Map<LocalDate, DailyPrice> rows = new TreeMap<>();
            for (JsonNode bar : root.path("bars")) {
                LocalDate date = LocalDate.parse(bar.path("date").asString());
                if (date.isBefore(from) || date.isAfter(to)) throw new IllegalArgumentException("Date outside request");
                BigDecimal close = number(bar, "close"), open = number(bar, "open"),
                        high = number(bar, "high"), low = number(bar, "low");
                if (close == null || close.signum() <= 0 || (open != null && open.signum() <= 0)
                        || (low != null && (low.signum() <= 0 || low.compareTo(close) > 0 || open != null && low.compareTo(open) > 0))
                        || (high != null && (high.compareTo(close) < 0 || open != null && high.compareTo(open) < 0)))
                    throw new IllegalArgumentException("Invalid OHLC");
                BigDecimal volume = number(bar, "volume");
                Long shares = volume == null ? null : volume.longValueExact();
                if (shares != null && shares < 0) throw new IllegalArgumentException("Invalid volume");
                // The documented API does not specify adjusted-close or adjustment semantics.
                var row = new DailyPrice(date, open, high, low, close, null, shares);
                var previous = rows.putIfAbsent(date, row);
                if (previous != null && !previous.equals(row)) throw new IllegalArgumentException("Conflicting duplicate");
            }
            return List.copyOf(rows.values());
        } catch (Exception ex) { throw new Unavailable("INVALID_PROVIDER_DATA", 900); }
    }

    private BigDecimal number(JsonNode row, String field) {
        var value = row.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isNumber()) throw new IllegalArgumentException("Invalid numeric field");
        return value.decimalValue();
    }
}
