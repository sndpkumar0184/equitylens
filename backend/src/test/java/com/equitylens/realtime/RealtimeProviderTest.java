package com.equitylens.realtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.equitylens.realtime.RealTimeMarketDataProvider.Capability.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.math.BigDecimal;

class RealtimeProviderTest {
    private final JsonMapper mapper=JsonMapper.builder().build();
    private AlpacaRealTimeMarketDataProvider alpaca() { return new AlpacaRealTimeMarketDataProvider(mapper,
        "wss://stream.data.alpaca.markets/v2/iex","test-key","test-secret","iex",2,Set.of(TRADES,QUOTES,BARS)); }
    private TiingoRealTimeMarketDataProvider tiingo(int threshold) { return new TiingoRealTimeMarketDataProvider(mapper,
        "wss://api.tiingo.com/iex","test-token",threshold,2); }
    @Test void alpacaNormalizesAllChannelsAndStableIdentity() {
        var p=alpaca();
        String body="""
            [{"T":"t","S":"AAPL","i":42,"x":"V","p":100.25,"s":10,"t":"2026-09-30T14:00:10Z","c":["@"],"z":"C"},
             {"T":"q","S":"AAPL","bp":100,"bs":1,"ap":101,"as":2,"bx":"V","ax":"V","t":"2026-09-30T14:00:11Z"},
             {"T":"b","S":"AAPL","o":100,"h":102,"l":99,"c":101,"v":20,"vw":100.5,"t":"2026-09-30T14:00:00Z"}]
            """;
        var events=p.parse(body);assertEquals(3,events.size());assertEquals("ALPACA",events.getFirst().provider());assertEquals("IEX",events.getFirst().feed());
        assertEquals(p.parse(body).getFirst().eventId(),events.getFirst().eventId());
        assertEquals("ROUND_LOTS",((CanonicalMarketEvent.MarketQuoteEvent)events.get(1).data()).sizeUnit());
        assertEquals(new BigDecimal("100.5"),((CanonicalMarketEvent.MarketBarEvent)events.get(2).data()).vwap());
        var json=mapper.readTree(mapper.writeValueAsString(events.getFirst()));assertEquals(1,json.path("schemaVersion").asInt());
        assertEquals(10,json.path("data").path("quantity").asInt());assertFalse(json.has("T"));
        assertEquals("2026-09-30T14:00:10.000000000Z",events.getFirst().eventTime());
    }
    @Test void tiingoUsesOwnArrayPositionsAndNeverCountsQuoteAsTrade() {
        var p=tiingo(0);
        var t=p.parse("""
          {"service":"iex","messageType":"A","data":["T","2026-09-30T14:00:10Z",1790776810000000000,"META",null,null,null,null,null,200,12,0,0,0,1,0]}
          """).getFirst();
        assertEquals("TRADE",t.eventType());assertEquals(new BigDecimal("12"),((CanonicalMarketEvent.MarketTradeEvent)t.data()).quantity());
        var q=p.parse("""
          {"service":"iex","messageType":"A","data":["Q","2026-09-30T14:00:10Z",1790776810000000000,"META",10,199,200,201,20,null,null]}
          """).getFirst();
        assertEquals("QUOTE",q.eventType());assertEquals("SHARES",((CanonicalMarketEvent.MarketQuoteEvent)q.data()).sizeUnit());
        assertEquals("IEX_TOPS",q.feed());assertFalse(p.capabilities().supported().contains(BARS));
    }
    @Test void referenceOnlyIsNotFabricatedTrade() {
        var p=tiingo(6);assertEquals(Set.of(REFERENCE_PRICE),p.capabilities().supported());
        var e=p.parse("""
          {"service":"iex","messageType":"A","data":["2026-09-30T14:00:00Z","NVDA",120.5]}
          """).getFirst();
        assertEquals("REFERENCE_PRICE",e.eventType());assertFalse(mapper.readTree(mapper.writeValueAsString(e)).path("data").has("quantity"));
        assertThrows(UnsupportedOperationException.class,()->p.subscribe(Set.of("AAPL"),Set.of(TRADES)));
    }
    @Test void subscriptionValidationAndDisconnect() {
        var p=alpaca();p.subscribe(Set.of("aapl"),Set.of(TRADES));p.subscribe(Set.of("META"),Set.of(TRADES));
        assertThrows(IllegalArgumentException.class,()->p.subscribe(Set.of("NVDA"),Set.of(TRADES)));
        p.unsubscribe(Set.of("AAPL"));p.subscribe(Set.of("NVDA"),Set.of(TRADES));p.disconnect();
        assertEquals("DISCONNECTED",p.status().get("state"));assertThrows(RuntimeException.class,()->p.subscribe(Set.of("*"),Set.of(TRADES)));
        assertThrows(UnsupportedOperationException.class,()->p.subscribe(Set.of("META"),Set.of(NEWS)));
    }
    @Test void errorsMalformedMissingAndBreaksAreVisible() {
        var a=alpaca();a.acceptMessage("bad json");assertEquals(1L,a.status().get("invalidMessages"));
        assertThrows(RuntimeException.class,()->a.parse("[{\"T\":\"t\",\"S\":\"AAPL\",\"t\":\"2026-09-30T14:00:00Z\"}]"));
        a.parse("[{\"T\":\"error\",\"code\":405,\"msg\":\"limit\"}]");assertEquals(1L,a.status().get("providerErrors"));
        a.parse("[{\"T\":\"c\"}]");assertEquals(1L,a.status().get("unsupportedEvents"));
        var t=tiingo(0);assertTrue(t.parse("{\"service\":\"iex\",\"messageType\":\"A\",\"data\":[\"B\",\"2026-09-30T14:00:00Z\",0,\"AAPL\",null,null,null,null,null,100,1]}").isEmpty());
        t.parse("{\"messageType\":\"E\",\"response\":{\"code\":401}}");assertEquals(1L,t.status().get("providerErrors"));
        assertThrows(RuntimeException.class,()->tiingo(6).parse("{\"service\":\"iex\",\"messageType\":\"A\",\"data\":[1,2]}"));
    }
    @Test void connectionConfigurationRejectsSecretsInUrlsAndMissingCredentials() {
        assertThrows(IllegalArgumentException.class,()->new TiingoRealTimeMarketDataProvider(mapper,"ws://localhost","",6,2));
        assertThrows(IllegalArgumentException.class,()->new TiingoRealTimeMarketDataProvider(mapper,"wss://api.tiingo.com/iex?token=secret","",6,2));
        assertThrows(IllegalArgumentException.class,()->new TiingoRealTimeMarketDataProvider(mapper,"wss://api.tiingo.com/iex","",6,2).connect(e->{}));
        assertThrows(IllegalArgumentException.class,()->new AlpacaRealTimeMarketDataProvider(mapper,"wss://stream.data.alpaca.markets/v2/sip","","","iex",2,Set.of(TRADES)));
    }
}
