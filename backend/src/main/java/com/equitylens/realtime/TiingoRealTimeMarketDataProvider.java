package com.equitylens.realtime;

import java.util.*;
import tools.jackson.databind.*;
import static com.equitylens.realtime.CanonicalMarketEvent.*;

public final class TiingoRealTimeMarketDataProvider extends WebSocketMarketProvider {
    private final String token;
    private final int threshold;
    private volatile Long subscriptionId;
    public TiingoRealTimeMarketDataProvider(ObjectMapper mapper,String url,String token,int threshold,int maxSymbols) {
        super(mapper,url,maxSymbols);this.token=token;this.threshold=threshold;
        if(!Set.of(0,5,6).contains(threshold))throw new IllegalArgumentException("Tiingo threshold must be 0, 5 or 6");
    }
    public Capabilities capabilities() { return new Capabilities(threshold==6?Set.of(Capability.REFERENCE_PRICE):Set.of(Capability.TRADES,Capability.QUOTES),
        "TIINGO",threshold==6?"IEX_REFERENCE":threshold==5?"IEX_TOPS_FILTERED":"IEX_TOPS","IEX only; TOPS requires exchange agreement; level 5 quotes are filtered"); }
    protected void validateCredentials() { if(token.isBlank())throw new IllegalArgumentException("Tiingo token required"); }
    protected void sendAuth() { subscriptionId=null; authenticated=true;sendSubscription(); }
    protected void sendSubscription() {
        Map<String,Object> data=new HashMap<>();data.put("thresholdLevel",threshold);data.put("tickers",symbols);
        if(subscriptionId!=null)data.put("subscriptionId",subscriptionId);
        send(Map.of("eventName","subscribe","authorization",token,"eventData",data));
    }
    protected void sendUnsubscribe(Set<String> removed) { /* Full desired subscription replaces previous subscription by ID. */ }
    protected void afterUnsubscribe() { if(authenticated)sendSubscription(); }
    public List<CanonicalMarketEvent> parse(String text) {
        JsonNode n=mapper.readTree(text);
        if(n.path("response").path("code").asInt(200)>=400 || "E".equals(n.path("messageType").asString())) { providerError();return List.of(); }
        String type=n.path("messageType").asString();
        if("I".equals(type)) { if(n.path("data").has("subscriptionId")) { subscriptionId=n.path("data").path("subscriptionId").asLong();subscribed(); } return List.of(); }
        if("H".equals(type))return List.of();
        if(!"A".equals(type) || !"iex".equals(n.path("service").asString())) { unsupportedEvent();return List.of(); }
        JsonNode d=n.path("data"); if(!d.isArray())throw new IllegalArgumentException("Expected Tiingo array");
        if(threshold==6) {
            if(d.size()!=3)throw new IllegalArgumentException("Reference schema mismatch");
            return List.of(event("REFERENCE_PRICE",d.get(1).asString(),d.get(0).asString(),d.toString(),null,Map.of("thresholdLevel",threshold),new ReferencePriceEvent(number(d.get(2)))));
        }
        if(d.size()<11)throw new IllegalArgumentException("TOPS schema mismatch");
        String kind=d.get(0).asString(),time=d.get(1).asString(),symbol=d.get(3).asString();
        Map<String,Object> metadata=new HashMap<>();metadata.put("thresholdLevel",threshold);metadata.put("timestampNanoseconds",d.get(2).asString());
        for(int i=11;i<d.size();i++) if(!d.get(i).isNull())metadata.put("flag"+i,d.get(i).asInt());
        if("T".equals(kind))return List.of(event("TRADE",symbol,time,d.toString(),"IEX",metadata,new MarketTradeEvent(number(d.get(9)),number(d.get(10)))));
        if("Q".equals(kind))return List.of(event("QUOTE",symbol,time,d.toString(),"IEX",metadata,new MarketQuoteEvent(number(d.get(5)),number(d.get(4)),
            number(d.get(7)),number(d.get(8)),"SHARES","IEX","IEX")));
        unsupportedEvent(); return List.of(); // Breaks are counted, never treated as new volume.
    }
}
