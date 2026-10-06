package com.equitylens.realtime;

import org.apache.kafka.clients.producer.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.ObjectMapper;

public final class CanonicalKafkaPublisher implements AutoCloseable {
    private final Producer<String,String> producer;
    private final ObjectMapper mapper;
    public CanonicalKafkaPublisher(Producer<String,String> producer,ObjectMapper mapper) { this.producer=producer;this.mapper=mapper; }
    public static Properties settings(String bootstrap) {
        var p=new Properties();p.put("bootstrap.servers",bootstrap);p.put("key.serializer","org.apache.kafka.common.serialization.StringSerializer");
        p.put("value.serializer","org.apache.kafka.common.serialization.StringSerializer");p.put("acks","all");p.put("enable.idempotence","true");
        p.put("delivery.timeout.ms",15000);p.put("request.timeout.ms",5000);p.put("max.block.ms",5000);p.put("buffer.memory",8388608);
        p.put("client.id","equitylens-market-ingest");return p;
    }
    public static String topic(String type) { return switch(type) {
        case "TRADE" -> "equitylens.market.trade.v1"; case "QUOTE" -> "equitylens.market.quote.v1";
        case "BAR" -> "equitylens.market.bar.v1";case "REFERENCE_PRICE" -> "equitylens.market.reference.v1";
        default -> throw new IllegalArgumentException("Unsupported canonical type"); }; }
    public void publish(CanonicalMarketEvent event) {
        try { producer.send(new ProducerRecord<>(topic(event.eventType()),event.symbol(),mapper.writeValueAsString(event))).get(20,TimeUnit.SECONDS); }
        catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException("Kafka publish interrupted"); }
        catch(Exception e) { throw new IllegalStateException("Kafka publish failed"); }
    }
    public void close() { producer.close(Duration.ofSeconds(5)); }
}
