package com.equitylens.realtime;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/market/realtime")
public class MarketRealtimeController {
    private final MarketAnalyticsService service;
    private final MarketLiveHub hub;
    public MarketRealtimeController(MarketAnalyticsService service,MarketLiveHub hub) { this.service=service;this.hub=hub; }
    @GetMapping("/{symbol}") public Map<String,Object> latest(@PathVariable String symbol) { return service.getLatestMarketState(symbol); }
    @GetMapping("/{symbol}/bars") public List<Map<String,Object>> bars(@PathVariable String symbol,
            @RequestParam(defaultValue="1m") String interval,@RequestParam(defaultValue="60") int rangeMinutes,@RequestParam(defaultValue="100") int limit) {
        return service.getRecentBars(symbol,interval,rangeMinutes,limit);
    }
    @GetMapping("/{symbol}/analytics") public List<Map<String,Object>> analytics(@PathVariable String symbol,
            @RequestParam(defaultValue="60") int rangeMinutes,@RequestParam(defaultValue="100") int limit) {
        return service.getRollingMetrics(symbol,rangeMinutes,limit);
    }
    @GetMapping("/{symbol}/anomalies") public List<Map<String,Object>> anomalies(@PathVariable String symbol,
            @RequestParam(defaultValue="60") int rangeMinutes,@RequestParam(defaultValue="100") int limit) {
        return service.getRecentAnomalies(symbol,rangeMinutes,limit);
    }
    @GetMapping(value="/{symbol}/events",produces="text/event-stream") public SseEmitter events(@PathVariable String symbol) { return hub.subscribe(symbol); }
}
