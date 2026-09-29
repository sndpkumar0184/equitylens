package com.equitylens.providers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class FinnhubMarketDataProvider implements MarketQuoteProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public FinnhubMarketDataProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${finnhub.base-url}") String baseUrl,
            @Value("${finnhub.api-key}") String apiKey
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();

        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public MarketQuote getQuote(String ticker) {

        try {

            /*
             * 1. Real-time quote
             *
             * c  = current price
             * pc = previous close
             * t  = quote timestamp
             */
            JsonNode quote =
                    get(
                            "/quote",
                            ticker,
                            null
                    );

            /*
             * 2. Company profile
             *
             * marketCapitalization and shareOutstanding
             * are reported by Finnhub in millions.
             */
            JsonNode profile =
                    get(
                            "/stock/profile2",
                            ticker,
                            null
                    );

            /*
             * 3. Basic financial metrics
             *
             * Provides:
             * - 52-week high
             * - 52-week low
             * - beta
             */
            JsonNode metrics =
                    get(
                            "/stock/metric",
                            ticker,
                            "all"
                    );

            BigDecimal currentPrice =
                    decimal(
                            quote,
                            "c"
                    );

            BigDecimal previousClose =
                    decimal(
                            quote,
                            "pc"
                    );

            BigDecimal sharesOutstanding =
                    decimal(
                            profile,
                            "shareOutstanding"
                    );

            BigDecimal marketCap =
                    decimal(
                            profile,
                            "marketCapitalization"
                    );

            /*
             * Finnhub reports these two profile values
             * in millions.
             *
             * Convert to absolute values.
             */
            if (sharesOutstanding != null) {

                sharesOutstanding =
                        sharesOutstanding.multiply(
                                BigDecimal.valueOf(1_000_000)
                        );
            }

            if (marketCap != null) {

                marketCap =
                        marketCap.multiply(
                                BigDecimal.valueOf(1_000_000)
                        );
            }

            /*
             * Finnhub /stock/metric response contains
             * the "metric" object.
             */
            JsonNode metric =
                    metrics.get("metric");

            if (metric == null ||
                    metric.isNull()) {

                metric = metrics;
            }

            BigDecimal fiftyTwoWeekHigh =
                    firstDecimal(
                            metric,
                            "52WeekHigh",
                            "52WeekHighAdjusted"
                    );

            BigDecimal fiftyTwoWeekLow =
                    firstDecimal(
                            metric,
                            "52WeekLow",
                            "52WeekLowAdjusted"
                    );

            BigDecimal beta =
                    decimal(
                            metric,
                            "beta"
                    );

            /*
             * Finnhub's quote timestamp is Unix epoch seconds.
             */
            JsonNode timestampNode =
                    quote.get("t");

            LocalDateTime timestamp;

            if (timestampNode != null &&
                    timestampNode.isNumber()) {

                timestamp =
                        Instant
                                .ofEpochSecond(
                                        timestampNode.longValue()
                                )
                                .atZone(
                                        ZoneOffset.UTC
                                )
                                .toLocalDateTime();

            } else {

                timestamp =
                        LocalDateTime.now(
                                ZoneOffset.UTC
                        );
            }

            return new MarketQuote(
                    currentPrice,
                    previousClose,
                    sharesOutstanding,
                    marketCap,
                    fiftyTwoWeekHigh,
                    fiftyTwoWeekLow,
                    beta,
                    timestamp
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to fetch Finnhub market data for "
                            + ticker,
                    e
            );
        }
    }

    private JsonNode get(
            String endpoint,
            String ticker,
            String metric
    ) throws Exception {

        String response =
                restClient.get()
                        .uri(uriBuilder -> {

                            var builder =
                                    uriBuilder
                                            .path(endpoint)
                                            .queryParam(
                                                    "symbol",
                                                    ticker
                                            )
                                            .queryParam(
                                                    "token",
                                                    apiKey
                                            );

                            if (metric != null) {

                                builder.queryParam(
                                        "metric",
                                        metric
                                );
                            }

                            return builder.build();
                        })
                        .retrieve()
                        .body(String.class);

        return objectMapper.readTree(
                response
        );
    }

    private BigDecimal firstDecimal(
            JsonNode node,
            String... fields
    ) {

        for (String field : fields) {

            BigDecimal value =
                    decimal(
                            node,
                            field
                    );

            if (value != null) {
                return value;
            }
        }

        return null;
    }

    private BigDecimal decimal(
            JsonNode node,
            String field
    ) {

        if (node == null) {
            return null;
        }

        JsonNode value =
                node.get(field);

        if (value == null ||
                value.isNull() ||
                !value.isNumber()) {

            return null;
        }

        return value.decimalValue();
    }
}
