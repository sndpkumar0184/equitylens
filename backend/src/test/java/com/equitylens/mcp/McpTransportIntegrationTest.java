package com.equitylens.mcp;

import com.equitylens.ai.*;
import com.equitylens.dto.CompanyResponse;
import com.equitylens.entity.Company;
import com.equitylens.service.*;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"equitylens.ai.provider=test","equitylens.ai.access-token=integration-test-token"})
class McpTransportIntegrationTest {
    @LocalServerPort int port;
    @MockitoBean CompanyService companies;
    @MockitoBean DashboardService dashboard;
    @MockitoBean FinancialDataService financials;
    @MockitoBean MarketHistoryService market;
    @MockitoBean AIProvider provider;
    @Autowired ObjectMapper mapper;
    @BeforeEach void setup() {
        when(companies.getCompany(anyString())).thenAnswer(i->new Company(i.getArgument(0),"0000000001","Test",null,null));
        when(dashboard.getDashboard(anyString(),anyInt())).thenAnswer(i->ResearchToolsTest.dashboard(i.getArgument(0)));
        when(financials.getResearchSources(anyString(),any(),any())).thenReturn(List.of());
    }
    @Test void sdkInitializesDiscoversAndCallsStructuredToolsOverHttp() {
        var transport=HttpClientStreamableHttpTransport.builder("http://127.0.0.1:"+port).endpoint("/mcp").openConnectionOnStartup(false)
                .requestBuilder(HttpRequest.newBuilder().header("Authorization","Bearer integration-test-token")).build();
        try(var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build()) {
            var init=client.initialize();assertEquals("equitylens",init.serverInfo().name());
            assertEquals(10,client.listTools().tools().size());
            for(String ticker:List.of("META","AAPL","AMZN","NVDA")) {
                var result=client.callTool(new CallToolRequest("get_company",Map.of("ticker",ticker)));
                assertFalse(result.isError());assertEquals(ticker,ResearchToolsTest.data(result).get("ticker"));
            }
            var result=client.callTool(new CallToolRequest("get_financial_history",Map.of("ticker","AAPL","metric","revenue")));
            assertFalse(result.isError());assertTrue(result.structuredContent().toString().contains("value=100"));
            assertEquals("UNSUPPORTED_CAPABILITY",ResearchToolsTest.data(client.callTool(new CallToolRequest("get_price_history",Map.of("ticker","AAPL","timeframe","hourly")))).get("status"));
            assertTrue(client.callTool(new CallToolRequest("get_company",Map.of("ticker",42))).isError());
        }
    }
    @Test void assistantUsesActualMcpClientAndReturnsEvidenceWithExplicitContext() {
        var connections=new EquityLensMcpClient("http://127.0.0.1:"+port,"integration-test-token");
        @SuppressWarnings("unchecked") org.springframework.beans.factory.ObjectProvider<AIProvider> providers=mock(org.springframework.beans.factory.ObjectProvider.class);
        when(providers.getIfAvailable()).thenReturn(provider);
        when(provider.complete(anyList(),anyList())).thenReturn(new AIProvider.Message("assistant","",List.of(new AIProvider.ToolCall("get_financial_history",Map.of("ticker","META","metric","revenue"))),null))
                .thenReturn(AIProvider.Message.text("assistant","Based on EquityLens data: META FY2025 revenue was $100 (fixture). Interpretation is uncertain."));
        var service=new AssistantService(providers,connections,mapper,true);
        var result=service.chat(new AssistantService.Request("META",List.of(new AssistantService.Turn("user","How did revenue change?"))));
        assertEquals("META",result.ticker());assertEquals(1,result.evidence().size());assertEquals("get_financial_history",result.evidence().getFirst().tool());
        verify(dashboard).getDashboard("META",10);
        verify(provider,times(2)).complete(argThat(messages->messages.getFirst().content().contains("Active company context: META")),anyList());
    }
    @Test void authAndOriginsAreCheckedAndRestRemainsAccessible()throws Exception {
        var http=HttpClient.newHttpClient();String base="http://127.0.0.1:"+port;
        assertEquals(403,http.send(HttpRequest.newBuilder(URI.create(base+"/mcp")).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(403,http.send(HttpRequest.newBuilder(URI.create(base+"/mcp")).header("Authorization","Bearer integration-test-token").header("Origin","https://evil.example").GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(200,http.send(HttpRequest.newBuilder(URI.create(base+"/api/companies/AAPL")).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
    }
}
