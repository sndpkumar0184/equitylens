package com.equitylens.realtime;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.equitylens.service.Ticker;
import jakarta.annotation.PreDestroy;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Derived notifications are coalesced; browsers receive persisted application state, never raw ticks. */
@Component
public class MarketLiveHub {
    private final Map<SseEmitter,String> clients=new ConcurrentHashMap<>();
    private final Set<String> dirty=ConcurrentHashMap.newKeySet();
    private final MarketAnalyticsService service;
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"market-sse");t.setDaemon(true);return t;});
    private final AtomicLong clock=new AtomicLong();
    public MarketLiveHub(MarketAnalyticsService service) {
        this.service=service;timer.scheduleWithFixedDelay(this::flush,1,1,TimeUnit.SECONDS);
    }
    public synchronized SseEmitter subscribe(String input) {
        String symbol=Ticker.normalize(input);
        if(clients.size()>=200)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Live subscriber capacity reached");
        var emitter=new SseEmitter(300000L);clients.put(emitter,symbol);
        Runnable remove=()->clients.remove(emitter);emitter.onCompletion(remove);emitter.onTimeout(remove);emitter.onError(e->remove.run());
        dirty.add(symbol);return emitter;
    }
    public void changed(String symbol) { if(clients.containsValue(symbol))dirty.add(symbol); }
    private void flush() {
        boolean heartbeat=clock.incrementAndGet()%15==0;
        Set<String> updates=new HashSet<>();for(String s:dirty)if(dirty.remove(s))updates.add(s);
        Map<String,Object> states=new HashMap<>();
        for(var entry:clients.entrySet()) {
            try {
                if(updates.contains(entry.getValue())) {
                    Object state=states.computeIfAbsent(entry.getValue(),service::getLatestMarketState);
                    entry.getKey().send(SseEmitter.event().name("market").data(state));
                } else if(heartbeat)entry.getKey().send(SseEmitter.event().comment("heartbeat"));
            } catch(Exception ex) { clients.remove(entry.getKey());entry.getKey().complete(); }
        }
    }
    @PreDestroy public void close() { timer.shutdownNow();clients.keySet().forEach(SseEmitter::complete); }
}
