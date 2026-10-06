package com.equitylens.realtime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
class MarketAnalyticsServiceTest {
    @Test void normalizesAndBoundsAllReadsAndKeepsSourceIsolated() {
        var repository=mock(MarketAnalyticsRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<RealTimeMarketDataProvider> provider=mock(ObjectProvider.class);
        var environment=new MockEnvironment().withProperty("realtime.analytics.provider","TIINGO").withProperty("realtime.tiingo.threshold","6");
        var service=new MarketAnalyticsService(repository,environment,provider);
        service.getRecentBars("aapl","5m",60,10);verify(repository).bars("AAPL","5m",60,10,"TIINGO","IEX_REFERENCE",false);
        for(int range:List.of(0,10081))assertThrows(ResponseStatusException.class,()->service.getRollingMetrics("AAPL",range,10));
        for(int limit:List.of(0,501))assertThrows(ResponseStatusException.class,()->service.getRecentAnomalies("AAPL",60,limit));
        assertThrows(ResponseStatusException.class,()->service.getRecentBars("AAPL","daily",60,10));
        assertThrows(ResponseStatusException.class,()->service.getLatestMarketState("';DROP"));
    }
    @Test void latestIncludesReferenceWithoutInventingBarsOrMetrics() {
        var repository=mock(MarketAnalyticsRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<RealTimeMarketDataProvider> provider=mock(ObjectProvider.class);
        when(repository.reference("META","TIINGO","IEX_REFERENCE",false)).thenReturn(List.of(Map.of("price",123)));
        var service=new MarketAnalyticsService(repository,new MockEnvironment().withProperty("realtime.analytics.provider","TIINGO"),provider);
        var state=service.getLatestMarketState("meta");assertEquals("AVAILABLE",state.get("status"));assertNull(state.get("bar"));assertNull(state.get("analytics"));
        assertEquals(Map.of("price",123),state.get("reference"));assertEquals("TIINGO",state.get("provider"));
    }
}
