package com.equitylens.realtime;

import java.util.*;
import tools.jackson.databind.*;
import static com.equitylens.realtime.CanonicalMarketEvent.*;

public final class AlpacaRealTimeMarketDataProvider extends WebSocketMarketProvider {
    private final String key,secret,feed;
    private final Set<Capability> supported;
    public AlpacaRealTimeMarketDataProvider(ObjectMapper mapper,String url,String key,String secret,String feed,
            int maxSymbols,Set<Capability> supported) {
        super(mapper,url,maxSymbols);this.key=key;this.secret=secret;this.feed=feed;this.supported=Set.copyOf(supported);
        if(!Set.of(Capability.TRADES,Capability.QUOTES,Capability.BARS).containsAll(supported)
                || !endpoint.getPath().endsWith("/"+feed.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Feed/endpoint/channel mismatch");
    }
    public Capabilities capabilities() { return new Capabilities(supported,"ALPACA",feed.toUpperCase(Locale.ROOT),"Feed-specific coverage; entitlement verified by provider authentication"); }
    protected void validateCredentials() { if(key.isBlank() || secret.isBlank()) throw new IllegalArgumentException("Alpaca credentials required"); }
    protected void sendAuth() { send(Map.of("action","auth","key",key,"secret",secret)); }
    private Map<String,Object> subscription(String action,Set<String> tickers) {
        Map<String,Object> m=new HashMap<>();m.put("action",action);
        if(channels.contains(Capability.TRADES))m.put("trades",tickers);
        if(channels.contains(Capability.QUOTES))m.put("quotes",tickers);
        if(channels.contains(Capability.BARS))m.put("bars",tickers); return m;
    }
    protected void sendSubscription() { send(subscription("subscribe",symbols)); }
    protected void sendUnsubscribe(Set<String> tickers) { send(subscription("unsubscribe",tickers)); }
    private String optionalText(JsonNode n,String key) { return !n.has(key) || n.get(key).isNull()?null:n.path(key).asString(); }
    public List<CanonicalMarketEvent> parse(String text) {
        JsonNode root=mapper.readTree(text);if(!root.isArray())throw new IllegalArgumentException("Expected array");
        List<CanonicalMarketEvent> events=new ArrayList<>();
        for(JsonNode n:root) {
            String type=n.path("T").asString();
            if("success".equals(type)) { if("authenticated".equals(n.path("msg").asString()))ready();continue; }
            if("subscription".equals(type)) { subscribed();continue; }
            if("error".equals(type)) { providerError();continue; }
            if(!Set.of("t","q","b").contains(type)) { unsupportedEvent();continue; }
            var capability=switch(type){case "t"->Capability.TRADES;case "q"->Capability.QUOTES;default->Capability.BARS;};
            if(!supported.contains(capability)) { unsupportedEvent();continue; }
            String symbol=n.path("S").asString(),time=n.path("t").asString();
            var metadata=new HashMap<String,Object>();
            if(n.has("i"))metadata.put("tradeId",n.path("i").asString());
            if("b".equals(type) && n.has("n"))metadata.put("tradeCount",n.path("n").asLong());
            if(n.has("c") && !n.get("c").isNull() && !"b".equals(type)) metadata.put("conditions",mapper.convertValue(n.get("c"),List.class));
            if(n.has("z"))metadata.put("tape",n.path("z").asString());
            switch(type) {
                case "t" -> events.add(event("TRADE",symbol,time,n.path("x").asString()+":"+n.path("i").asString()+":"+n.path("p")+":"+n.path("s"),
                    optionalText(n,"x"),metadata,new MarketTradeEvent(number(n.get("p")),number(n.get("s")))));
                case "q" -> events.add(event("QUOTE",symbol,time,n.toString(),null,metadata,new MarketQuoteEvent(number(n.get("bp")),number(n.get("bs")),
                    number(n.get("ap")),number(n.get("as")),"ROUND_LOTS",optionalText(n,"bx"),optionalText(n,"ax"))));
                case "b" -> events.add(event("BAR",symbol,time,n.toString(),null,metadata,new MarketBarEvent("1m",number(n.get("o")),number(n.get("h")),
                    number(n.get("l")),number(n.get("c")),number(n.get("v")),number(n.get("vw")))));
            }
        } return events;
    }
}
