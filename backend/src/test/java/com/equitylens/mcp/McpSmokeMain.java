package com.equitylens.mcp;

import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.*;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Optional live verification; never run as part of the deterministic JUnit suite. */
public class McpSmokeMain {
    public static void main(String[] args)throws Exception {
        String base=args.length>0?args[0]:"http://127.0.0.1:8080";
        var request=HttpRequest.newBuilder();String token=System.getenv("AI_ACCESS_TOKEN");
        if(token!=null&&!token.isBlank())request.header("Authorization","Bearer "+token);
        var transport=HttpClientStreamableHttpTransport.builder(base).endpoint("/mcp").requestBuilder(request).openConnectionOnStartup(false).build();
        var mapper=new ObjectMapper();var http=HttpClient.newHttpClient();
        try(var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(120)).build()) {
            var init=client.initialize();System.out.println("MCP negotiated: "+init.protocolVersion());
            if(client.listTools().tools().size()!=10)throw new AssertionError("Expected ten tools");
            for(String ticker:List.of("META","AAPL","AMZN","NVDA")) {
                var identity=client.callTool(new CallToolRequest("get_company",Map.of("ticker",ticker)));
                require(identity,"identity "+ticker);
                var financial=client.callTool(new CallToolRequest("get_financials",Map.of("ticker",ticker,"limit",1)));
                require(financial,"financials "+ticker);
                var json=mapper.valueToTree(financial.structuredContent());
                if(!ticker.equals(json.path("ticker").asText())||json.path("periods").isEmpty())throw new AssertionError("Missing financials "+ticker);
                var rest=http.send(HttpRequest.newBuilder(URI.create(base+"/api/companies/"+ticker+"/dashboard")).GET().build(),HttpResponse.BodyHandlers.ofString());
                if(rest.statusCode()!=200)throw new AssertionError("REST dashboard failed "+ticker);
                var dashboard=mapper.readTree(rest.body());var income=dashboard.path("annual").path("income");
                var latest=income.get(income.size()-1);var actual=json.path("periods").get(0);
                if(!latest.path("periodEnd").asText().equals(actual.path("periodEnd").asText()) || latest.path("revenue").decimalValue().compareTo(actual.path("revenue").decimalValue()) != 0)throw new AssertionError("MCP/REST revenue mismatch "+ticker);
                for(String tool:List.of("get_financial_history","get_growth_metrics","get_profitability_metrics","get_market_snapshot")) {
                    var input=new HashMap<String,Object>();input.put("ticker",ticker);if(tool.equals("get_financial_history"))input.put("metric","revenue");
                    var result=client.callTool(new CallToolRequest(tool,input));require(result,tool+" "+ticker);
                    if(tool.equals("get_market_snapshot"))System.out.println(ticker+" market: "+mapper.valueToTree(result.structuredContent()).path("status").asText());
                }
                for(String interval:List.of("daily","monthly","yearly"))require(client.callTool(new CallToolRequest("get_price_history",Map.of("ticker",ticker,"timeframe",interval))),"prices "+ticker+" "+interval);
                var hourly=mapper.valueToTree(client.callTool(new CallToolRequest("get_price_history",Map.of("ticker",ticker,"timeframe","hourly"))).structuredContent());
                if(!"UNSUPPORTED_CAPABILITY".equals(hourly.path("status").asText()))throw new AssertionError("Unexpected hourly capability");
                var valuation=client.callTool(new CallToolRequest("get_valuation_metrics",Map.of("ticker",ticker)));
                System.out.println(ticker+" financials/REST parity/margins/growth/history: PASS; valuation: "+(Boolean.TRUE.equals(valuation.isError())?"DATA_UNAVAILABLE":"OK"));
            }
            require(client.callTool(new CallToolRequest("search_companies",Map.of("query","apple"))),"search");
            require(client.callTool(new CallToolRequest("compare_companies",Map.of("tickers",List.of("META","AAPL","AMZN","NVDA")))),"comparison");
            System.out.println("Search, comparison and all four tickers: PASS");
        }
    }
    private static void require(CallToolResult r,String operation){if(Boolean.TRUE.equals(r.isError()))throw new AssertionError(operation+": "+r.content());}
}
