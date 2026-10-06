package com.equitylens.mcp;

import com.equitylens.dto.*;
import com.equitylens.entity.Company;
import com.equitylens.providers.MarketDataProvider.Interval;
import com.equitylens.service.*;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResearchToolsTest {
    CompanyService companies=mock(CompanyService.class);
    DashboardService dashboards=mock(DashboardService.class);
    FinancialDataService financials=mock(FinancialDataService.class);
    MarketHistoryService market=mock(MarketHistoryService.class);
    ValuationService valuations=mock(ValuationService.class);
    ResearchTools tools=new ResearchTools(companies,dashboards,financials,market,valuations,new ObjectMapper());
    static BigDecimal n(String value){return new BigDecimal(value);}
    public static DashboardResponse dashboard(String ticker) {
        var end=LocalDate.of(2025,12,31);var start=LocalDate.of(2025,1,1);
        var data=new DashboardResponse.PeriodData(null,
                List.of(new IncomeStatementResponse("FY",start,end,n("100"),n("60"),n("40"),n("25"),n("20"),"USD")),
                List.of(new DashboardResponse.Growth("FY",start,end,n("12.5"))),
                List.of(new DashboardResponse.Profitability("FY",start,end,n("40"),n("25"),n("20"))),
                List.of(new CashFlowResponse("FY",start,end,n("30"),n("5"),null,null,n("25"),"USD")),
                List.of(new DashboardResponse.Balance("FY",end,"USD",n("10"),n("15"),n("80"),n("50"))));
        return new DashboardResponse(new CompanyResponse(ticker,"0000000001",ticker+" Inc.",null,null,1L),data,new DashboardResponse.PeriodData(null,List.of(),List.of(),List.of(),List.of(),List.of()));
    }
    public static MarketHistoryResponse market(String ticker,Interval interval) {
        return new MarketHistoryResponse(ticker,"TEST_PROVIDER","USD","STALE",Instant.parse("2026-01-03T00:00:00Z"),null,
                "PROVIDER_CLOSE_UNVERIFIED_ADJUSTMENTS",n("100"),LocalDate.of(2026,1,2),n("2"),n("2.04"),1000L,n("110"),n("80"),null,null,null,null,
                interval,Set.of(Interval.DAILY,Interval.MONTHLY,Interval.YEARLY),List.of(new DailyPrice(LocalDate.of(2026,1,2),n("98"),n("101"),n("97"),n("100"),null,1000L)));
    }
    @BeforeEach void setup() {
        when(companies.getCompany(anyString())).thenAnswer(i->new Company(i.getArgument(0),"0000000001","Test Company",null,null));
        when(dashboards.getDashboard(anyString(),anyInt())).thenAnswer(i->dashboard(i.getArgument(0)));
        when(financials.getResearchSources(anyString(),any(),any())).thenReturn(List.of(new FinancialDataService.FilingSource("revenue",LocalDate.of(2025,1,1),LocalDate.of(2025,12,31),LocalDate.of(2026,2,1),"10-K",null,"USD")));
        when(market.getMarket(anyString(),isNull(),isNull())).thenAnswer(i->market(i.getArgument(0),Interval.DAILY));
        when(market.getMarket(anyString(),any(),any(),any())).thenAnswer(i->market(i.getArgument(0),i.getArgument(3)));
        when(valuations.getValuation(anyString())).thenAnswer(i->new ValuationResponse(i.getArgument(0),LocalDate.of(2025,12,31),null,null,null,null,null,null,null,null,null,null,n("20"),null,null,null,null));
    }
    @SuppressWarnings("unchecked") static Map<String,Object> data(CallToolResult result){return (Map<String,Object>)result.structuredContent();}
    @ParameterizedTest @ValueSource(strings={"META","AAPL","AMZN","NVDA"})
    void allTickerToolsReuseServicesAndPreserveRequestedIdentity(String ticker) {
        for(String name:List.of("get_company","get_financials","get_financial_history","get_growth_metrics","get_profitability_metrics","get_market_snapshot","get_price_history","get_valuation_metrics")) {
            var args=new HashMap<String,Object>();args.put("ticker",ticker.toLowerCase(Locale.ROOT));
            if(name.equals("get_financial_history"))args.put("metric","revenue");
            if(name.equals("get_price_history"))args.put("timeframe","daily");
            var result=tools.call(name,args);assertFalse(result.isError(),name+": "+result.content());
            if(!name.equals("get_company"))assertEquals(ticker,data(result).get("ticker"));
            else assertEquals(ticker,((CompanyResponse)result.structuredContent()).ticker());
        }
        verify(companies).getCompany(ticker);verify(dashboards,times(4)).getDashboard(ticker,10);
        verify(market).getMarket(ticker,null,null);verify(market).getMarket(ticker,null,null,Interval.DAILY);verify(valuations).getValuation(ticker);
    }
    @Test void financialOutputsUseExistingCalculationResultsAndFilingMetadata() {
        var result=data(tools.call("get_financials",Map.of("ticker","AAPL")));
        assertTrue(result.toString().contains("freeCashFlow=25"));assertTrue(result.toString().contains("operatingMargin=25"));
        assertTrue(result.toString().contains("filingDate=2026-02-01"));assertEquals("percentage_points",result.get("percentageUnit"));
        var growth=data(tools.call("get_growth_metrics",Map.of("ticker","AAPL")));
        assertTrue(growth.toString().contains("revenueGrowth=12.5"));assertTrue(growth.containsKey("unavailableMetrics"));
        var history=data(tools.call("get_financial_history",Map.of("ticker","AAPL","metric","revenue","start","2025-01-01","end","2025-12-31")));
        assertTrue(history.toString().contains("value=100"));assertEquals("revenue",history.get("metric"));
    }
    @Test void searchIsBoundedAndEmptySearchIsNotAnError() {
        when(companies.searchCompanies("apple")).thenReturn(List.of(new CompanyResponse("AAPL","1","Apple",null,null,1L)));
        assertFalse(tools.call("search_companies",Map.of("query","apple")).isError());verify(companies).searchCompanies("apple");
        when(companies.searchCompanies("unmatched")).thenReturn(List.of());assertEquals(List.of(),data(tools.call("search_companies",Map.of("query","unmatched"))).get("companies"));
        assertTrue(tools.call("search_companies",Map.of("query"," ")).isError());
    }
    @Test void comparisonIsNormalizedAndRetainsIndividualErrors() {
        when(dashboards.getDashboard("ZZZZ",10)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"unknown"));
        var result=tools.call("compare_companies",Map.of("tickers",List.of("AAPL","META","ZZZZ"),"metrics",List.of("revenue","freeCashFlow")));
        assertFalse(result.isError());assertTrue(result.structuredContent().toString().contains("NOT_FOUND"));
        assertTrue(result.structuredContent().toString().contains("periodEnd=2025-12-31"));
        verify(dashboards).getDashboard("AAPL",10);verify(dashboards).getDashboard("META",10);
        assertTrue(tools.call("compare_companies",Map.of("tickers",List.of("AAPL","aapl"))).isError());
        assertTrue(tools.call("compare_companies",Map.of("tickers",List.of("AAPL"))).isError());
        assertTrue(tools.call("compare_companies",Map.of("tickers",List.of("A","B"),"metrics",List.of("fake"))).isError());
    }
    @Test void missingFinancialDataAndUnavailableValuationAreExplicit() {
        when(dashboards.getDashboard("AAPL",10)).thenReturn(new DashboardResponse(dashboard("AAPL").company(),dashboard("AAPL").quarterly(),dashboard("AAPL").quarterly()));
        assertEquals("EMPTY",data(tools.call("get_financials",Map.of("ticker","AAPL"))).get("status"));
        when(valuations.getValuation("AAPL")).thenThrow(new RuntimeException("secret-token"));
        var result=tools.call("get_valuation_metrics",Map.of("ticker","AAPL"));assertTrue(result.isError());assertFalse(result.content().toString().contains("secret-token"));
    }
    @Test void hourlyIsNeverFabricatedAndOtherIntervalsUseMarketService() {
        assertEquals("UNSUPPORTED_CAPABILITY",data(tools.call("get_price_history",Map.of("ticker","AAPL","timeframe","hourly"))).get("status"));verifyNoInteractions(market);
        for(String interval:List.of("daily","monthly","yearly")) {
            var result=tools.call("get_price_history",Map.of("ticker","AAPL","timeframe",interval));assertFalse(result.isError());
            assertTrue(result.structuredContent().toString().contains("timestamp=2026-01-02"));
        }
        var snapshot=data(tools.call("get_market_snapshot",Map.of("ticker","AAPL")));
        assertEquals("STALE",snapshot.get("status"));assertEquals("TEST_PROVIDER",snapshot.get("source"));assertFalse(snapshot.containsKey("history"));
    }
    @Test void providerErrorsAndEmptyPricesStayExplicit() {
        when(market.getMarket("AAPL",null,null)).thenThrow(new RuntimeException("https://provider/secret-key"));
        var result=tools.call("get_market_snapshot",Map.of("ticker","AAPL"));assertTrue(result.isError());assertFalse(result.content().toString().contains("secret-key"));
        var empty=new MarketHistoryResponse("AAPL","TEST","USD","EMPTY",null,null,null,null,null,null,null,null,null,null,null,null,null,null,Interval.DAILY,Set.of(Interval.DAILY),List.of());
        when(market.getMarket("AAPL",null,null,Interval.DAILY)).thenReturn(empty);
        assertEquals(List.of(),data(tools.call("get_price_history",Map.of("ticker","AAPL","timeframe","daily"))).get("candles"));
    }
    @Test void everyToolRejectsMalformedInputsBeforeServicesAndErrorsAreSanitized() {
        for(var tool:tools.catalog()) {
            assertTrue(tools.call(tool.name(),Map.of()).isError(),tool.name());
            assertTrue(tools.call(tool.name(),Map.of("unexpected","bad")).isError(),tool.name());
            if(!tool.name().equals("search_companies")&&!tool.name().equals("compare_companies")) {
                var args=new HashMap<String,Object>();args.put("ticker","AAPL';DROP TABLE companies");
                if(tool.name().equals("get_price_history"))args.put("timeframe","daily");
                if(tool.name().equals("get_financial_history"))args.put("metric","revenue");
                assertTrue(tools.call(tool.name(),args).isError());
            }
        }
        for(var args:List.of(Map.<String,Object>of("ticker","AAPL","limit",999),Map.<String,Object>of("ticker","AAPL","start","bad"),
                Map.<String,Object>of("ticker","AAPL","start","2026-01-01","end","2025-01-01"),Map.<String,Object>of("ticker","AAPL","type","fake")))
            assertEquals("INVALID_PARAMETERS",data(tools.call("get_financials",args)).get("status"));
        assertTrue(tools.call("get_price_history",Map.of("ticker","AAPL","timeframe","minute")).isError());
        verifyNoInteractions(companies,dashboards,financials,market,valuations);
    }
    @Test void validButUnknownTickersAreReportedForEveryDataService() {
        var error=new ResponseStatusException(HttpStatus.NOT_FOUND,"unknown");
        when(companies.getCompany("ZZZZ")).thenThrow(error);when(dashboards.getDashboard("ZZZZ",10)).thenThrow(error);
        when(market.getMarket("ZZZZ",null,null)).thenThrow(error);when(market.getMarket("ZZZZ",null,null,Interval.DAILY)).thenThrow(error);when(valuations.getValuation("ZZZZ")).thenThrow(error);
        for(String name:List.of("get_company","get_financials","get_financial_history","get_growth_metrics","get_profitability_metrics","get_market_snapshot","get_price_history","get_valuation_metrics")) {
            var args=new HashMap<String,Object>();args.put("ticker","ZZZZ");if(name.equals("get_financial_history"))args.put("metric","revenue");if(name.equals("get_price_history"))args.put("timeframe","daily");
            assertEquals("NOT_FOUND",data(tools.call(name,args)).get("status"),name);
        }
    }
}
