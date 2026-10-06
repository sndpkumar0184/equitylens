package com.equitylens.realtime;

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketAnalyticsConsumerTest {
    private ConsumerRecords<String,String> records(String... values) {
        var partition=new TopicPartition("equitylens.analytics.market.v1",0);
        var rows=new java.util.ArrayList<ConsumerRecord<String,String>>();
        for(int i=0;i<values.length;i++)rows.add(new ConsumerRecord<>(partition.topic(),0,i,"AMZN",values[i]));
        return new ConsumerRecords<>(Map.of(partition,rows));
    }
    private final String event="{\"schemaVersion\":1,\"eventType\":\"MARKET_ANALYTICS\",\"symbol\":\"amzn\"}";

    @Test void validAndDuplicateEventsOnlyNotifyPersistedStateReader() {
        var hub=mock(MarketLiveHub.class);
        MarketAnalyticsConsumer.process(records(event,event),new ObjectMapper(),hub);
        verify(hub,times(2)).changed("AMZN");
        verifyNoMoreInteractions(hub);
    }
    @Test void invalidMessagesDoNotPreventNextValidMessage() {
        var hub=mock(MarketLiveHub.class);
        MarketAnalyticsConsumer.process(records("broken",event.replace(":1",":2"),event.replace("amzn","bad!"),event),new ObjectMapper(),hub);
        verify(hub).changed("AMZN");
        verifyNoMoreInteractions(hub);
    }
    @Test void applicationFailurePropagatesBeforeAcknowledgementAndCanRetry() {
        var hub=mock(MarketLiveHub.class);
        doThrow(new IllegalStateException("unavailable")).doNothing().when(hub).changed("AMZN");
        assertThrows(IllegalStateException.class,()->MarketAnalyticsConsumer.process(records(event),new ObjectMapper(),hub));
        assertDoesNotThrow(()->MarketAnalyticsConsumer.process(records(event),new ObjectMapper(),hub));
        verify(hub,times(2)).changed("AMZN");
    }
}
