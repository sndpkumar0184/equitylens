package com.equitylens.realtime;

import java.util.Set;
import java.util.function.Consumer;

/** Independent of the historical MarketDataProvider contract. One active source per process. */
public interface RealTimeMarketDataProvider extends AutoCloseable {
    enum Capability { TRADES, QUOTES, BARS, NEWS, REFERENCE_PRICE }
    record Capabilities(Set<Capability> supported, String provider, String feed, String coverage) {}
    Capabilities capabilities();
    void connect(Consumer<CanonicalMarketEvent> sink);
    void subscribe(Set<String> symbols, Set<Capability> channels);
    void unsubscribe(Set<String> symbols);
    void disconnect();
    java.util.Map<String, Object> status();
    default void close() { disconnect(); }
}
