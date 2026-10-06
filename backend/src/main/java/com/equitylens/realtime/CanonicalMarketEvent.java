package com.equitylens.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import com.equitylens.service.Ticker;

/** EquityLens v1 wire contract. Prices are USD; trade quantities are shares. */
public record CanonicalMarketEvent(int schemaVersion, String eventId, String eventType,
        String symbol, String eventTime, String receivedAt, String provider, String feed,
        String exchange, Map<String, Object> sourceMetadata, Object data) {
    public record MarketTradeEvent(BigDecimal price, BigDecimal quantity) {
        public MarketTradeEvent { positive(price); positive(quantity); }
    }
    public record MarketQuoteEvent(BigDecimal bidPrice, BigDecimal bidSize, BigDecimal askPrice,
            BigDecimal askSize, String sizeUnit, String bidExchange, String askExchange) {
        public MarketQuoteEvent {
            nonnegative(bidPrice); nonnegative(askPrice); nonnegative(bidSize); nonnegative(askSize);
            if (bidPrice == null && askPrice == null) throw new IllegalArgumentException("Empty quote");
        }
    }
    public record MarketBarEvent(String interval, BigDecimal open, BigDecimal high, BigDecimal low,
            BigDecimal close, BigDecimal volume, BigDecimal vwap) {
        public MarketBarEvent {
            positive(open); positive(high); positive(low); positive(close); nonnegative(volume); nonnegative(vwap);
            if (!"1m".equals(interval) || volume == null || low.compareTo(high)>0 || high.compareTo(open)<0
                    || high.compareTo(close)<0 || low.compareTo(open)>0 || low.compareTo(close)>0)
                throw new IllegalArgumentException("Invalid bar");
        }
    }
    public record ReferencePriceEvent(BigDecimal price) { public ReferencePriceEvent { positive(price); } }
    public CanonicalMarketEvent {
        if (schemaVersion != 1 || eventId == null || eventId.isBlank() || provider == null || provider.isBlank()
                || feed == null || feed.isBlank()) throw new IllegalArgumentException("Invalid event metadata");
        symbol = Ticker.normalize(symbol);
        var format = new java.time.format.DateTimeFormatterBuilder().appendInstant(9).toFormatter();
        eventTime = format.format(Instant.parse(eventTime)); receivedAt = format.format(Instant.parse(receivedAt));
        boolean valid = switch(eventType) {
            case "TRADE" -> data instanceof MarketTradeEvent;
            case "QUOTE" -> data instanceof MarketQuoteEvent;
            case "BAR" -> data instanceof MarketBarEvent;
            case "REFERENCE_PRICE" -> data instanceof ReferencePriceEvent;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("Invalid event type/data");
        sourceMetadata = Map.copyOf(sourceMetadata);
        if("SYNTHETIC".equals(provider) && !Boolean.TRUE.equals(sourceMetadata.get("synthetic")))
            throw new IllegalArgumentException("Synthetic data requires an explicit marker");
    }
    /** Version-gated typed decoder; raw provider payloads are not accepted here. */
    @SuppressWarnings("unchecked")
    public static CanonicalMarketEvent decode(String json, tools.jackson.databind.ObjectMapper mapper) {
        var n=mapper.readTree(json);
        if(n.path("schemaVersion").asInt()!=1) throw new IllegalArgumentException("Unsupported canonical schema version");
        String type=n.path("eventType").asString();
        Class<?> dataType=switch(type) {
            case "TRADE" -> MarketTradeEvent.class; case "QUOTE" -> MarketQuoteEvent.class;
            case "BAR" -> MarketBarEvent.class; case "REFERENCE_PRICE" -> ReferencePriceEvent.class;
            default -> throw new IllegalArgumentException("Unsupported canonical event type");
        };
        return new CanonicalMarketEvent(1,n.path("eventId").asString(),type,n.path("symbol").asString(),
            n.path("eventTime").asString(),n.path("receivedAt").asString(),n.path("provider").asString(),n.path("feed").asString(),
            !n.has("exchange") || n.get("exchange").isNull()?null:n.path("exchange").asString(),
            mapper.convertValue(n.path("sourceMetadata"),Map.class),mapper.convertValue(n.path("data"),dataType));
    }
    private static void positive(BigDecimal n) { if(n==null || n.signum()<=0) throw new IllegalArgumentException("Positive value required"); }
    private static void nonnegative(BigDecimal n) { if(n!=null && n.signum()<0) throw new IllegalArgumentException("Negative value"); }
}
