package com.equitylens.realtime;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import com.equitylens.service.Ticker;
import tools.jackson.databind.*;

/** Bounded frames and explicit demand: publishing blocks demand rather than creating an unbounded tick queue. */
public abstract class WebSocketMarketProvider implements RealTimeMarketDataProvider {
    protected final ObjectMapper mapper;
    protected final URI endpoint;
    protected final int maxSymbols;
    protected volatile WebSocket socket;
    protected Set<String> symbols = Set.of();
    protected Set<Capability> channels = Set.of();
    protected volatile boolean authenticated;
    private volatile boolean running;
    private volatile String state = "DISCONNECTED";
    private volatile Instant lastMessage;
    private volatile String lastFailure = "";
    private final AtomicLong generation = new AtomicLong();
    private Consumer<CanonicalMarketEvent> sink;
    private ScheduledExecutorService executor;
    private int attempts;
    private final AtomicLong received = new AtomicLong(), normalized = new AtomicLong(), invalid = new AtomicLong(),
        reconnects = new AtomicLong(), errors = new AtomicLong(), unsupported = new AtomicLong(), publishFailures = new AtomicLong();
    protected WebSocketMarketProvider(ObjectMapper mapper, String url, int maxSymbols) {
        this.mapper=mapper; endpoint=URI.create(url); this.maxSymbols=maxSymbols;
        if(!"wss".equals(endpoint.getScheme()) || endpoint.getUserInfo()!=null || endpoint.getQuery()!=null)
            throw new IllegalArgumentException("Configure a secure server-side stream endpoint without credentials");
        if(maxSymbols<1 || maxSymbols>1000) throw new IllegalArgumentException("Subscription limit must be 1..1000");
    }
    public synchronized void subscribe(Set<String> input, Set<Capability> requested) {
        if(!capabilities().supported().containsAll(requested)) throw new UnsupportedOperationException("Unsupported channel");
        Set<String> next = new TreeSet<>(symbols); input.forEach(s -> next.add(Ticker.normalize(s)));
        if(next.size()>maxSymbols || requested.isEmpty()) throw new IllegalArgumentException("Invalid subscription size/channels");
        if(authenticated) { symbols=Set.copyOf(next); channels=Set.copyOf(requested); sendSubscription(); }
        else { symbols=Set.copyOf(next); channels=Set.copyOf(requested); }
    }
    public synchronized void unsubscribe(Set<String> input) {
        Set<String> removed=new TreeSet<>(); input.forEach(s -> removed.add(Ticker.normalize(s)));
        if(authenticated) sendUnsubscribe(removed);
        Set<String> next=new TreeSet<>(symbols); next.removeAll(removed); symbols=Set.copyOf(next);
        afterUnsubscribe();
    }
    protected void afterUnsubscribe() {}
    public synchronized void connect(Consumer<CanonicalMarketEvent> sink) {
        if(running) return; validateCredentials(); this.sink=sink; running=true;
        executor=Executors.newSingleThreadScheduledExecutor(r -> { var t=new Thread(r,"market-provider");t.setDaemon(true);return t; });
        executor.execute(this::open);
        executor.scheduleWithFixedDelay(() -> {
            if(running && lastMessage!=null && lastMessage.isBefore(Instant.now().minusSeconds(60))) failure("STALE_CONNECTION");
        }, 30,30,TimeUnit.SECONDS);
    }
    private void open() {
        if(!running) return; state="CONNECTING"; authenticated=false; lastMessage=Instant.now();
        long current=generation.incrementAndGet();
        dial(new WebSocket.Listener() {
                private final StringBuilder frame=new StringBuilder();
                public void onOpen(WebSocket ws) { if(!running || generation.get()!=current) { ws.abort();return; } socket=ws; state="AUTHENTICATING"; sendAuth(); ws.request(1); }
                public CompletionStage<?> onText(WebSocket ws,CharSequence text,boolean last) {
                    if(!running || generation.get()!=current) return null;
                    if(frame.length()+text.length()>1048576) { failure("OVERSIZED_FRAME"); return null; }
                    frame.append(text); if(last) { acceptMessage(frame.toString()); frame.setLength(0); }
                    ws.request(1); return null;
                }
                public CompletionStage<?> onClose(WebSocket ws,int code,String reason) { if(generation.get()==current) failure("DISCONNECTED"); return null; }
                public void onError(WebSocket ws,Throwable error) { if(generation.get()==current)failure("CONNECTION_ERROR"); }
                public CompletionStage<?> onPing(WebSocket ws,java.nio.ByteBuffer message) { ws.request(1); return ws.sendPong(message); }
                public CompletionStage<?> onPong(WebSocket ws,java.nio.ByteBuffer message) { lastMessage=Instant.now();ws.request(1);return null; }
            }).exceptionally(ex -> { if(generation.get()==current)failure("CONNECTION_ERROR");return null; });
    }
    protected CompletionStage<WebSocket> dial(WebSocket.Listener listener) {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10)).buildAsync(endpoint,listener);
    }
    public void acceptMessage(String text) {
        lastMessage=Instant.now(); received.incrementAndGet();
        List<CanonicalMarketEvent> events;
        try { events=parse(text); } catch(Exception ex) { invalid.incrementAndGet();return; }
        for(var event:events) {
            normalized.incrementAndGet();
            try { if(sink!=null) sink.accept(event); } catch(Exception ex) { publishFailures.incrementAndGet();failure("DATA_GAP_KAFKA_FAILURE");break; }
        }
    }
    protected synchronized void ready() { authenticated=true; attempts=0; state="SUBSCRIBING"; sendSubscription(); }
    protected void subscribed() { attempts=0; state="LIVE"; }
    protected void providerError() { errors.incrementAndGet(); failure("PROVIDER_ERROR"); }
    protected void unsupportedEvent() { unsupported.incrementAndGet(); }
    protected synchronized void failure(String reason) {
        if(!running || "RECONNECTING".equals(state)) return;
        state="RECONNECTING";lastFailure=reason; authenticated=false;generation.incrementAndGet();
        if(socket!=null) socket.abort(); socket=null; reconnects.incrementAndGet();
        long delay=Math.min(60,1L<<Math.min(attempts++,6));
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Market stream {}: {}; reconnect in {}s (data gap possible)", capabilities().provider(),reason,delay);
        executor.schedule(this::open,delay,TimeUnit.SECONDS);
    }
    protected void send(Object message) {
        if(socket!=null) socket.sendText(mapper.writeValueAsString(message),true)
            .whenComplete((ws,ex)->{ if(ex!=null) failure("SEND_ERROR"); });
    }
    public synchronized void disconnect() { running=false;authenticated=false;generation.incrementAndGet();state="DISCONNECTED";
        if(socket!=null) socket.sendClose(1000,"Shutdown"); socket=null;
        if(executor!=null) executor.shutdownNow();
    }
    public Map<String,Object> status() { return Map.ofEntries(Map.entry("state",state),Map.entry("capabilities",capabilities()),
        Map.entry("lastFailure",lastFailure),Map.entry("reconnects",reconnects.get()),Map.entry("messagesReceived",received.get()),Map.entry("eventsNormalized",normalized.get()),
        Map.entry("invalidMessages",invalid.get()),Map.entry("providerErrors",errors.get()),Map.entry("unsupportedEvents",unsupported.get()),
        Map.entry("publishFailures",publishFailures.get()),Map.entry("lastMessage",lastMessage==null?"":lastMessage.toString())); }
    protected abstract void validateCredentials();
    protected abstract void sendAuth();
    protected abstract void sendSubscription();
    protected abstract void sendUnsubscribe(Set<String> symbols);
    public abstract List<CanonicalMarketEvent> parse(String text);
    protected java.math.BigDecimal number(JsonNode node) {
        if(node==null || node.isNull() || node.isMissingNode()) return null;
        if(!node.isNumber()) throw new IllegalArgumentException("Invalid number"); return node.decimalValue();
    }
    protected CanonicalMarketEvent event(String type,String symbol,String time,String id,String exchange,Map<String,Object> metadata,Object data) {
        String fingerprint=capabilities().provider()+"|"+capabilities().feed()+"|"+type+"|"+symbol+"|"+time+"|"+id;
        return new CanonicalMarketEvent(1,UUID.nameUUIDFromBytes(fingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
            type,symbol,time,Instant.now().toString(),capabilities().provider(),capabilities().feed(),exchange,metadata,data);
    }
}
