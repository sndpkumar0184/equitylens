package com.equitylens.service;

import com.equitylens.controller.MarketHistoryController;
import com.equitylens.dto.DailyPrice;
import com.equitylens.entity.Company;
import com.equitylens.entity.MarketPrice;
import com.equitylens.providers.DailyPriceProvider;
import com.equitylens.repository.*;
import com.equitylens.exception.ApiExceptionHandler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class MarketHistoryIntegrationTest {
    @Autowired MarketHistoryService service;
    @Autowired MarketHistoryController controller;
    @Autowired CompanyRepository companies;
    @Autowired MarketPriceRepository prices;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoBean DailyPriceProvider provider;
    private final LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
    private Company company;

    @BeforeEach void setup() {
        company = new Company(); company.setTicker("MKT.TEST"); company.setCik("0000000001"); company.setName("Market test");
        companies.saveAndFlush(company);
        when(provider.source()).thenReturn("TEST_PROVIDER");
        when(provider.supportedIntervals()).thenReturn(java.util.Set.of(com.equitylens.providers.MarketDataProvider.Interval.DAILY));
    }
    private DailyPrice price(LocalDate date, int close) {
        return new DailyPrice(date, BigDecimal.valueOf(close), BigDecimal.valueOf(close+10),
                BigDecimal.valueOf(close-10), BigDecimal.valueOf(close), null, 1000L);
    }
    private void expire() {
        jdbc.update("update market_price_imports set retry_after = ? where company_id = ?", Instant.EPOCH.atOffset(ZoneOffset.UTC), company.getId());
        em.clear();
    }

    @Test void persistsRealFieldsAndRepeatedRequestsAreCached() {
        when(provider.getHistory(anyString(), any(), any())).thenReturn(List.of(price(today.minusDays(1), 100)));
        var result = service.getMarket("mkt.test", null, null);
        service.getMarket("MKT.TEST", null, null);
        assertEquals("MKT.TEST", result.ticker()); assertEquals("TEST_PROVIDER", result.source());
        assertEquals(0, result.latestPrice().compareTo(BigDecimal.valueOf(100)));
        assertEquals(1000L, result.history().getFirst().volume());
        assertNull(result.history().getFirst().adjustedClose());
        assertEquals(1, prices.countByCompanyIdAndSource(company.getId(), "TEST_PROVIDER"));
        verify(provider, times(1)).getHistory(eq("MKT.TEST"), any(), eq(today));
    }

    @Test void refreshUpsertsCorrectionsWithoutDuplicatingRowsAndPreservesLegacySource() {
        var legacy = new MarketPrice(); legacy.setCompany(company); legacy.setPriceDate(today.minusDays(1)); legacy.setClosePrice(BigDecimal.ONE);
        prices.saveAndFlush(legacy);
        when(provider.getHistory(anyString(), any(), any())).thenReturn(List.of(price(today.minusDays(1), 100)), List.of(price(today.minusDays(1), 110)));
        service.getMarket("MKT.TEST", null, null); expire();
        var result = service.getMarket("MKT.TEST", null, null);
        assertEquals(0, result.latestPrice().compareTo(BigDecimal.valueOf(110)));
        assertEquals(1, prices.countByCompanyIdAndSource(company.getId(), "TEST_PROVIDER"));
        assertEquals(1, prices.countByCompanyIdAndSource(company.getId(), "LEGACY"));
        // The unique index, not just service code, prevents duplicate source/date records.
        int inserted = jdbc.update("insert into market_prices (company_id,price_date,source,close_price) values (?,?,?,?) on conflict do nothing",
                company.getId(), today.minusDays(1), "TEST_PROVIDER", BigDecimal.TEN);
        assertEquals(0, inserted);
    }

    @Test void failedRefreshKeepsCachedPricesAndBacksOff() {
        when(provider.getHistory(anyString(), any(), any())).thenReturn(List.of(price(today.minusDays(1), 100)))
                .thenThrow(new DailyPriceProvider.Unavailable("RATE_LIMITED", 3600));
        service.getMarket("MKT.TEST", null, null); expire();
        var result = service.getMarket("MKT.TEST", null, null);
        assertEquals("STALE", result.status()); assertNotNull(result.latestPrice());
        assertTrue(result.retryAfter().isAfter(Instant.now().plusSeconds(3500)));
        service.getMarket("MKT.TEST", null, null);
        verify(provider, times(2)).getHistory(anyString(), any(), any());
    }

    @Test void emptyDataAndMissingCredentialsStayNullAndAreCached() {
        when(provider.getHistory(anyString(), any(), any())).thenReturn(List.of());
        var empty = service.getMarket("MKT.TEST", null, null);
        assertEquals("EMPTY", empty.status()); assertNull(empty.latestPrice()); assertNull(empty.high52Week());
        assertNull(empty.return1Year()); assertTrue(empty.history().isEmpty());
        service.getMarket("MKT.TEST", null, null);
        verify(provider, times(1)).getHistory(anyString(), any(), any());
        expire();
        when(provider.getHistory(anyString(), any(), any())).thenThrow(new DailyPriceProvider.Unavailable("NOT_CONFIGURED", 0));
        var missing = service.getMarket("MKT.TEST", null, null);
        assertEquals("NOT_CONFIGURED", missing.status()); assertNull(missing.latestPrice());
    }

    @Test void historicalChartRangeDoesNotChangeLatestAnalyticsAndExpandsCache() {
        when(provider.getHistory(anyString(), any(), any())).thenReturn(List.of(price(today.minusYears(2), 50), price(today.minusDays(1), 100)));
        var result = service.getMarket("MKT.TEST", today.minusYears(2), today.minusYears(2).plusDays(1));
        assertEquals(1, result.history().size()); assertEquals(today.minusYears(2), result.history().getFirst().date());
        assertEquals(today.minusDays(1), result.latestTradingDate());
        service.getMarket("MKT.TEST", today.minusYears(3), today);
        verify(provider).getHistory("MKT.TEST", today.minusYears(3), today);
    }

    @Test void returnsBackendMonthlyCandlesAndRejectsUnavailableHourlyData() {
        var rows = List.of(price(today.minusMonths(2).withDayOfMonth(3), 100),
                price(today.minusMonths(1).withDayOfMonth(4), 120), price(today.minusDays(4), 120), price(today.minusDays(1), 130));
        when(provider.getHistory(anyString(), any(), any())).thenReturn(rows);
        var monthly = service.getMarket("MKT.TEST", today.minusMonths(3), today, com.equitylens.providers.MarketDataProvider.Interval.MONTHLY);
        assertEquals(com.equitylens.providers.MarketDataProvider.Interval.MONTHLY, monthly.interval());
        assertEquals(3, monthly.history().size());
        assertEquals(today.withDayOfMonth(1), monthly.history().getLast().date());
        assertEquals(0, BigDecimal.valueOf(120).compareTo(monthly.history().getLast().open()));
        assertEquals(0, BigDecimal.valueOf(140).compareTo(monthly.history().getLast().high()));
        assertEquals(0, BigDecimal.valueOf(110).compareTo(monthly.history().getLast().low()));
        assertEquals(0, BigDecimal.valueOf(130).compareTo(monthly.history().getLast().close()));
        assertEquals(2000L, monthly.history().getLast().volume());
        var unsupported = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.getMarket("MKT.TEST", null, null, com.equitylens.providers.MarketDataProvider.Interval.HOURLY));
        assertEquals(422, unsupported.getStatusCode().value());
    }

    @Test void endpointValidatesDateRangesAndIsTickerDriven() throws Exception {
        when(provider.getHistory(anyString(), any(), any())).thenReturn(List.of(price(today.minusDays(1), 100)));
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        String json = mvc.perform(get("/api/companies/mkt.test/market")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(json.contains("MKT.TEST")); assertTrue(json.contains("latestPrice"));
        mvc.perform(get("/api/companies/MKT.TEST/market").param("from", today.toString()).param("to", today.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/companies/MKT.TEST/market").param("from", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/companies/MKT.TEST/market").param("to", today.plusDays(1).toString())).andExpect(status().isBadRequest());
    }
}
