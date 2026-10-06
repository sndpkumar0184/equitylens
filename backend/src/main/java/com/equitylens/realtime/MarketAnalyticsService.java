package com.equitylens.realtime;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.equitylens.service.Ticker;

/** Reusable application boundary for REST and future MCP tools. No Kafka queries. */
@Service
public class MarketAnalyticsService {
    private final MarketAnalyticsRepository repository;
    private final ObjectProvider<RealTimeMarketDataProvider> provider;
    private final String source,feed;
    private final boolean synthetic;
    public MarketAnalyticsService(MarketAnalyticsRepository repository,Environment env,ObjectProvider<RealTimeMarketDataProvider> provider) {
        this.repository=repository;this.provider=provider;
        source=env.getProperty("realtime.analytics.provider","ALPACA").toUpperCase(Locale.ROOT);
        String selectedFeed=env.getProperty("realtime.analytics.feed","");
        if(selectedFeed.isBlank()) {
            int threshold=env.getProperty("realtime.tiingo.threshold",Integer.class,6);
            selectedFeed="TIINGO".equals(source)?(threshold==6?"IEX_REFERENCE":threshold==5?"IEX_TOPS_FILTERED":"IEX_TOPS"):
                env.getProperty("realtime.alpaca.feed","iex");
        }
        feed=selectedFeed.toUpperCase(Locale.ROOT);
        synthetic=env.getProperty("realtime.analytics.synthetic",Boolean.class,false);
    }
    public Map<String,Object> getLatestMarketState(String input) {
        String symbol=Ticker.normalize(input);var bars=getRecentBars(symbol,"1m",1440,1);var metrics=getRollingMetrics(symbol,1440,1);
        Map<String,Object> result=new LinkedHashMap<>();result.put("symbol",symbol);result.put("provider",source);result.put("feed",feed);
        var references=repository.reference(symbol,source,feed,synthetic);result.put("reference",references.isEmpty()?null:references.getFirst());
        result.put("synthetic",synthetic);result.put("status",bars.isEmpty() && references.isEmpty()?"NO_DATA":"AVAILABLE");result.put("bar",bars.isEmpty()?null:bars.getFirst());
        result.put("analytics",metrics.isEmpty()?null:metrics.getFirst());
        var live=provider.getIfAvailable();result.put("ingestion",live==null?Map.of("state","DISABLED"):live.status());return result;
    }
    public List<Map<String,Object>> getRecentBars(String symbol,String interval,int minutes,int limit) {
        validate(minutes,limit);if(!Set.of("1m","5m").contains(interval))bad();
        return repository.bars(Ticker.normalize(symbol),interval,minutes,limit,source,feed,synthetic);
    }
    public List<Map<String,Object>> getRollingMetrics(String symbol,int minutes,int limit) {
        validate(minutes,limit);return repository.metrics(Ticker.normalize(symbol),minutes,limit,source,feed,synthetic,false);
    }
    public List<Map<String,Object>> getRecentAnomalies(String symbol,int minutes,int limit) {
        validate(minutes,limit);return repository.metrics(Ticker.normalize(symbol),minutes,limit,source,feed,synthetic,true);
    }
    private void validate(int minutes,int limit) { if(minutes<1 || minutes>10080 || limit<1 || limit>500)bad(); }
    private void bad() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use 1m/5m, rangeMinutes 1..10080 and limit 1..500"); }
}
