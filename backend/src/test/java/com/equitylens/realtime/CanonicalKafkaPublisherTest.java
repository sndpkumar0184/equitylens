package com.equitylens.realtime;
import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.Map;
class CanonicalKafkaPublisherTest {
    private CanonicalMarketEvent event() { return new CanonicalMarketEvent(1,"fixture","TRADE","AAPL","2026-09-30T14:00:00Z",
        "2026-09-30T14:00:01Z","SYNTHETIC","FIXTURE",null,Map.of("synthetic",true),new CanonicalMarketEvent.MarketTradeEvent(BigDecimal.TEN,BigDecimal.ONE)); }
    @Test void canonicalTopicSymbolKeyAndRoundTrip() {
        var mock=new MockProducer<String,String>(true,null,new StringSerializer(),new StringSerializer());var mapper=JsonMapper.builder().build();
        try(var publisher=new CanonicalKafkaPublisher(mock,mapper)) {
            publisher.publish(event());var r=mock.history().getFirst();assertEquals("equitylens.market.trade.v1",r.topic());assertEquals("AAPL",r.key());
            assertEquals(event(),CanonicalMarketEvent.decode(r.value(),mapper));
            assertThrows(IllegalArgumentException.class,()->CanonicalMarketEvent.decode(r.value().replace("\"schemaVersion\":1","\"schemaVersion\":2"),mapper));
            var json=mapper.readTree(r.value());assertEquals("TRADE",json.path("eventType").asString());assertEquals("fixture",json.path("eventId").asString());
            assertEquals("equitylens.market.reference.v1",CanonicalKafkaPublisher.topic("REFERENCE_PRICE"));
            assertThrows(IllegalArgumentException.class,()->CanonicalKafkaPublisher.topic("raw-provider"));
        }
    }
    @Test void producerErrorsPropagateWithoutPayload() {
        var mock=new MockProducer<String,String>(true,null,new StringSerializer(),new StringSerializer());mock.sendException=new RuntimeException("secret provider payload");
        var publisher=new CanonicalKafkaPublisher(mock,JsonMapper.builder().build());
        var ex=assertThrows(IllegalStateException.class,()->publisher.publish(event()));assertEquals("Kafka publish failed",ex.getMessage());assertNull(ex.getCause());publisher.close();
    }
    @Test void rejectsInvalidCanonicalVersionAndMissingPrice() {
        assertThrows(IllegalArgumentException.class,()->new CanonicalMarketEvent(2,"a","TRADE","AAPL","2026-09-30T14:00:00Z","2026-09-30T14:00:00Z","A","F",null,Map.of(),event().data()));
        assertThrows(IllegalArgumentException.class,()->new CanonicalMarketEvent.MarketTradeEvent(null,BigDecimal.ONE));
    }
}
