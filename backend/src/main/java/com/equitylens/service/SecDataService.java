package com.equitylens.service;

import com.equitylens.sec.SecCompanyFacts;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class SecDataService {
    public record SecCompany(String ticker, String cik, String name) {}
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String tickersUrl;
    private final String factsBaseUrl;
    private Map<String, SecCompany> directory = Map.of();
    private Instant refreshAfter = Instant.MIN;
    private long lastRequestNanos;

    public SecDataService(RestClient.Builder builder, ObjectMapper objectMapper,
            @Value("${sec.user-agent:EquityLens contact@example.com}") String userAgent,
            @Value("${sec.tickers-url:https://www.sec.gov/files/company_tickers.json}") String tickersUrl,
            @Value("${sec.facts-base-url:https://data.sec.gov}") String factsBaseUrl) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = builder.clone().requestFactory(factory).defaultHeader("User-Agent", userAgent).build();
        this.objectMapper = objectMapper;
        this.tickersUrl = tickersUrl;
        this.factsBaseUrl = factsBaseUrl;
    }

    // One directory download at a time; successful mappings are reused for 24 hours.
    public synchronized SecCompany resolveTicker(String input) {
        String ticker = Ticker.normalize(input);
        if (Instant.now().isAfter(refreshAfter)) {
            try {
                JsonNode root = getJson(tickersUrl);
                Map<String, SecCompany> next = new HashMap<>();
                for (JsonNode row : root) {
                    String symbol = row.path("ticker").asString("").trim().toUpperCase(java.util.Locale.ROOT);
                    String cik = row.path("cik_str").asString("");
                    String name = row.path("title").asString("");
                    if (!symbol.isBlank() && cik.matches("[0-9]{1,10}") && !name.isBlank()) {
                        next.put(symbol, new SecCompany(symbol, "0".repeat(10 - cik.length()) + cik, name));
                    }
                }
                if (next.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SEC returned an empty ticker directory");
                directory = Map.copyOf(next);
                refreshAfter = Instant.now().plus(Duration.ofHours(24));
            } catch (ResponseStatusException ex) {
                // Keep a previously downloaded directory usable during an SEC outage.
                if (directory.isEmpty() || !directory.containsKey(ticker)) throw ex;
                refreshAfter = Instant.now().plusSeconds(60);
            }
        }
        SecCompany result = directory.get(ticker);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown stock ticker: " + ticker);
        return result;
    }

    public SecCompanyFacts getCompanyFacts(String cik) {
        if (cik == null || !cik.matches("[0-9]{1,10}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SEC CIK");
        }
        String padded = "0".repeat(10 - cik.length()) + cik;
        return new SecCompanyFacts(getJson(factsBaseUrl + "/api/xbrl/companyfacts/CIK" + padded + ".json"));
    }

    // Serializing outbound calls also keeps this process below SEC's request-rate limit.
    private synchronized JsonNode getJson(String url) {
        try {
            long wait = Duration.ofMillis(150).toNanos() - (System.nanoTime() - lastRequestNanos);
            if (wait > 0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(wait);
            lastRequestNanos = System.nanoTime();
            String json = restClient.get().uri(url).retrieve().body(String.class);
            return objectMapper.readTree(json);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SEC request interrupted", ex);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SEC financial data is not available for this company", ex);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SEC is temporarily unavailable; please try again", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not retrieve valid SEC data; please try again", ex);
        }
    }
}
