package com.equitylens.realtime;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.errors.WakeupException;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;

@Component
@ConditionalOnProperty(name="realtime.live.enabled",havingValue="true")
public class MarketAnalyticsConsumer {
    private final KafkaConsumer<String,String> consumer;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"market-derived-consumer");t.setDaemon(true);return t;});
    private volatile boolean running=true;
    public MarketAnalyticsConsumer(Environment env,ObjectMapper mapper,MarketLiveHub hub) {
        var p=new Properties();p.put("bootstrap.servers",env.getProperty("realtime.kafka.bootstrap","localhost:9092"));
        // Each HTTP instance needs all derived notifications for its own connected browsers.
        p.put("group.id","equitylens-live-"+env.getProperty("realtime.live.instance-id",UUID.randomUUID().toString()));
        p.put("key.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
        p.put("value.deserializer","org.apache.kafka.common.serialization.StringDeserializer");p.put("auto.offset.reset","latest");
        p.put("enable.auto.commit",false);p.put("max.poll.records",500);
        consumer=new KafkaConsumer<>(p);
        worker.submit(()-> {
            consumer.subscribe(List.of("equitylens.analytics.market.v1"));
            try {
                while(running) {
                    try {
                        var records=consumer.poll(Duration.ofSeconds(1));
                        process(records,mapper,hub);
                        if(!records.isEmpty())consumer.commitSync();
                    }catch(WakeupException ex) { if(running)throw ex; }
                    catch(Exception ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Derived consumer unavailable; retrying; REST remains available");
                        try { Thread.sleep(1000); }catch(InterruptedException e){Thread.currentThread().interrupt();break;} }
                }
            }finally { consumer.close(Duration.ofSeconds(5)); }
        });
    }
    static void process(ConsumerRecords<String,String> records,ObjectMapper mapper,MarketLiveHub hub) {
        for(var record:records) {
            String symbol;
            try {
                var n=mapper.readTree(record.value());
                if(n.path("schemaVersion").asInt()!=1 || !"MARKET_ANALYTICS".equals(n.path("eventType").asString()))
                    throw new IllegalArgumentException();
                symbol=com.equitylens.service.Ticker.normalize(n.path("symbol").asString());
            }catch(Exception ex) {
                org.slf4j.LoggerFactory.getLogger(MarketAnalyticsConsumer.class).warn("Invalid derived event skipped at partition {} offset {}",record.partition(),record.offset());
                continue;
            }
            // Application failures propagate so the batch is not acknowledged and can retry.
            hub.changed(symbol);
        }
    }
    @PreDestroy public void close() { running=false;consumer.wakeup();worker.shutdown(); }
}
