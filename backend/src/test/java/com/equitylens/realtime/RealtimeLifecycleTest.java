package com.equitylens.realtime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.http.WebSocket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.json.JsonMapper;
import static com.equitylens.realtime.RealTimeMarketDataProvider.Capability.*;

class RealtimeLifecycleTest {
    @Test void bothProvidersAuthenticateSubscribeReconnectAndStopWithoutLiveCredentials() throws Exception {
        for(boolean alpaca:List.of(true,false)) {
            WebSocketMarketProvider provider=spy(alpaca?
                new AlpacaRealTimeMarketDataProvider(JsonMapper.builder().build(),"wss://stream.data.alpaca.markets/v2/iex","key","secret","iex",3,Set.of(TRADES)):
                new TiingoRealTimeMarketDataProvider(JsonMapper.builder().build(),"wss://api.tiingo.com/iex","token",0,3));
            var messages=new CopyOnWriteArrayList<String>();var listener=new AtomicReference<WebSocket.Listener>();
            var first=new CountDownLatch(1);var second=new CountDownLatch(1);
            var socket=mock(WebSocket.class);
            when(socket.sendText(any(),eq(true))).thenAnswer(call->{messages.add(call.getArgument(0).toString());return CompletableFuture.completedFuture(socket);});
            when(socket.sendClose(anyInt(),anyString())).thenReturn(CompletableFuture.completedFuture(socket));
            doAnswer(call->{var l=(WebSocket.Listener)call.getArgument(0);listener.set(l);l.onOpen(socket);
                if(first.getCount()>0)first.countDown();else second.countDown();return CompletableFuture.completedFuture(socket);}).when(provider).dial(any());
            provider.subscribe(Set.of("AAPL"),provider.capabilities().supported());provider.connect(e->{});
            assertTrue(first.await(2,TimeUnit.SECONDS));
            if(alpaca) { assertTrue(messages.getFirst().contains("auth"));provider.acceptMessage("[{\"T\":\"success\",\"msg\":\"authenticated\"}]"); }
            assertTrue(messages.stream().anyMatch(m->m.contains("AAPL") && m.contains("subscribe")));
            if(!alpaca)provider.acceptMessage("{\"messageType\":\"I\",\"data\":{\"subscriptionId\":7}}");
            provider.unsubscribe(Set.of("AAPL"));provider.subscribe(Set.of("META"),provider.capabilities().supported());
            assertTrue(messages.getLast().contains("META"));
            var obsolete=listener.get();obsolete.onClose(socket,1006,"test disconnect");
            assertTrue(second.await(3,TimeUnit.SECONDS));assertEquals(1L,provider.status().get("reconnects"));
            obsolete.onError(socket,new RuntimeException("stale"));assertEquals(1L,provider.status().get("reconnects"));
            provider.disconnect();assertEquals("DISCONNECTED",provider.status().get("state"));verify(socket).sendClose(1000,"Shutdown");
        }
    }
}
