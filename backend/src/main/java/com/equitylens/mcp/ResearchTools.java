package com.equitylens.mcp;

import com.equitylens.dto.*;
import com.equitylens.providers.MarketDataProvider;
import com.equitylens.service.*;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;

/** Input/output adapters only: normalization and calculations stay in application services. */
@Component
public class ResearchTools {
    public static final List<String> METRICS = List.of("revenue","netIncome","grossProfit","operatingIncome",
            "revenueGrowth","grossMargin","operatingMargin","netMargin","freeCashFlow","operatingCashFlow",
            "capitalExpenditures","cash","debt","assets","liabilities");
    private final CompanyService companies;
    private final DashboardService dashboards;
    private final FinancialDataService financials;
    private final MarketHistoryService market;
    private final ValuationService valuations;
    private final ObjectMapper mapper;
    private final Map<String,Tool> catalog = new LinkedHashMap<>();

    public ResearchTools(CompanyService companies, DashboardService dashboards, FinancialDataService financials,
            MarketHistoryService market, ValuationService valuations, ObjectMapper mapper) {
        this.companies=companies; this.dashboards=dashboards; this.financials=financials;
        this.market=market; this.valuations=valuations; this.mapper=mapper;
        var ticker=Map.<String,Object>of("type","string","minLength",1,"maxLength",10);
        var common=new LinkedHashMap<String,Object>();
        common.put("ticker",ticker);
        common.put("type",Map.of("type","string","enum",List.of("annual","quarterly"),"default","annual"));
        common.put("period",Map.of("type","string","pattern","^(FY|Q[1-4]|QUARTER)?[0-9]{4}$",
                "description","Reporting label FY2025, Q12025, or 2025 (period end year)"));
        common.put("limit",Map.of("type","integer","minimum",1,"maximum",40,"default",5));
        common.put("start",dateSchema()); common.put("end",dateSchema());
        add("search_companies","Search registered EquityLens companies by ticker/name. Maximum 20 results.",
                Map.of("query",Map.of("type","string","minLength",1,"maxLength",80)),List.of("query"));
        add("get_company","Company identity and available sector/industry.",Map.of("ticker",ticker),List.of("ticker"));
        add("get_financials","Normalized SEC metrics in USD. Defaults to 5 annual periods; max 10 annual/40 quarterly. Null means missing.",common,List.of("ticker"));
        var history=new LinkedHashMap<>(common); history.put("metric",Map.of("type","string","enum",METRICS));
        add("get_financial_history","Chronological values for one metric and reporting dates, with source observations.",history,List.of("ticker","metric"));
        add("get_growth_metrics","Existing revenue growth in percentage points. Earnings/FCF growth are unavailable.",common,List.of("ticker"));
        add("get_profitability_metrics","Existing gross, operating and net margins in percentage points.",common,List.of("ticker"));
        add("get_market_snapshot","Latest provider close, not a live quote; source/freshness, volume, changes, range and returns.",Map.of("ticker",ticker),List.of("ticker"));
        add("get_price_history","OHLCV candles capped at 366. Hourly unsupported: EquityLens stores daily observations. Narrow dates to avoid truncation.",
                Map.of("ticker",ticker,"timeframe",Map.of("type","string","enum",List.of("hourly","daily","monthly","yearly")),
                        "start",dateSchema(),"end",dateSchema()),List.of("ticker","timeframe"));
        add("compare_companies","Latest annual normalized metrics in USD; fiscal dates retained and may differ. Up to 5 companies with individual errors/missing data.",
                Map.of("tickers",Map.of("type","array","items",ticker,"minItems",2,"maxItems",5,"uniqueItems",true),
                        "metrics",Map.of("type","array","items",Map.of("type","string","enum",METRICS),"minItems",1,"maxItems",METRICS.size())),List.of("tickers"));
        add("get_valuation_metrics","Existing TTM valuations, requiring existing quote data. No new valuation model; unsupported values stay null.",Map.of("ticker",ticker),List.of("ticker"));
    }
    private static Map<String,Object> dateSchema() {return Map.of("type","string","format","date","pattern","^[0-9]{4}-[0-9]{2}-[0-9]{2}$","description","Inclusive ISO reporting/trading date");}
    private void add(String name,String description,Map<String,?> properties,List<String> required) {
        catalog.put(name,Tool.builder(name,Map.of("type","object","properties",properties,"required",required,"additionalProperties",false))
                .description(description).annotations(ToolAnnotations.builder().readOnlyHint(true).destructiveHint(false).openWorldHint(true).build()).build());
    }
    public List<Tool> catalog(){return List.copyOf(catalog.values());}
    public List<SyncToolSpecification> specifications(){return catalog.values().stream().map(t->SyncToolSpecification.builder().tool(t)
            .callHandler((exchange,request)->call(t.name(),request.arguments())).build()).toList();}
    public CallToolResult call(String name,Map<String,Object> args) {
        try {validate(name,args); return result(execute(name,args),false);}
        catch(ResponseStatusException ex) {
            int status=ex.getStatusCode().value();
            return result(Map.of("status",status==404?"NOT_FOUND":status==400?"INVALID_PARAMETERS":status==422?"UNSUPPORTED_CAPABILITY":"DATA_UNAVAILABLE",
                    "message",status==400?"Invalid tool parameters":"Requested EquityLens data or capability is unavailable"),true);
        } catch(IllegalArgumentException | java.time.DateTimeException ex) {return result(Map.of("status","INVALID_PARAMETERS","message","Check the tool schema, ticker, metric and ordered dates"),true);}
        catch(RuntimeException ex) {return result(Map.of("status","DATA_UNAVAILABLE","message","EquityLens could not retrieve this data; try again later"),true);}
    }
    private CallToolResult result(Object data,boolean error){return CallToolResult.builder().structuredContent(data).addTextContent(mapper.writeValueAsString(data)).isError(error).build();}
    private void validate(String name,Map<String,Object> args) {
        var tool=catalog.get(name); if(tool==null||args==null)throw new IllegalArgumentException();
        @SuppressWarnings("unchecked") var properties=(Map<String,Object>)tool.inputSchema().get("properties");
        if(!properties.keySet().containsAll(args.keySet()))throw new IllegalArgumentException();
        for(Object key:(List<?>)tool.inputSchema().get("required"))if(!args.containsKey(key))throw new IllegalArgumentException();
        for(String key:args.keySet()) {
            Object value=args.get(key); if(value==null)throw new IllegalArgumentException();
            if(!Set.of("limit","tickers","metrics").contains(key)&&!(value instanceof String))throw new IllegalArgumentException();
        }
        if(args.containsKey("ticker"))Ticker.normalize(string(args,"ticker",""));
        if(args.containsKey("type")&&!Set.of("annual","quarterly").contains(args.get("type")))throw new IllegalArgumentException();
        if(args.containsKey("period")&&!string(args,"period","").matches("(FY|Q[1-4]|QUARTER)?[0-9]{4}"))throw new IllegalArgumentException();
        limit(args); LocalDate start=date(args,"start"),end=date(args,"end");
        if(start!=null&&end!=null&&start.isAfter(end))throw new IllegalArgumentException();
        if(args.containsKey("metric")&&!METRICS.contains(args.get("metric")))throw new IllegalArgumentException();
        if(args.containsKey("timeframe")&&!Set.of("hourly","daily","monthly","yearly").contains(args.get("timeframe")))throw new IllegalArgumentException();
        if(args.containsKey("query")&&(string(args,"query","").isBlank()||string(args,"query","").length()>80))throw new IllegalArgumentException();
        if(args.containsKey("tickers")) {
            var ticks=strings(args.get("tickers"),2,5);
            if(ticks.stream().map(Ticker::normalize).distinct().count()!=ticks.size())throw new IllegalArgumentException();
        }
        if(args.containsKey("metrics")&&!METRICS.containsAll(strings(args.get("metrics"),1,METRICS.size())))throw new IllegalArgumentException();
    }
    private static List<String> strings(Object value,int min,int max) {
        if(!(value instanceof List<?> list)||list.size()<min||list.size()>max||list.stream().anyMatch(v->!(v instanceof String)))throw new IllegalArgumentException();
        return list.stream().map(String.class::cast).toList();
    }
    private static String string(Map<String,Object>a,String key,String fallback){return (String)a.getOrDefault(key,fallback);}
    private static LocalDate date(Map<String,Object>a,String key){return a.containsKey(key)?LocalDate.parse((String)a.get(key)):null;}
    private static int limit(Map<String,Object>a) {
        Object value=a.getOrDefault("limit",5);
        if(!(value instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<1||n.intValue()>40)throw new IllegalArgumentException();
        return n.intValue();
    }
    private Object execute(String name,Map<String,Object>a) {
        if(name.equals("search_companies"))return Map.of("companies",companies.searchCompanies((String)a.get("query")),"scope","registered_companies","limit",20);
        if(name.equals("compare_companies")) {
            var metrics=a.containsKey("metrics")?strings(a.get("metrics"),1,METRICS.size()):List.of("revenue","revenueGrowth","operatingMargin","freeCashFlow");
            var results=strings(a.get("tickers"),2,5).stream().map(t->{
                var r=call("get_financials",Map.of("ticker",t,"limit",1)); var data=map(r.structuredContent());
                if(!Boolean.TRUE.equals(r.isError())) {
                    @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)data.get("periods");
                    data.put("periods",rows.stream().map(row->select(row,metrics)).toList());
                }
                data.put("ticker",Ticker.normalize(t)); return data;
            }).toList();
            return Map.of("companies",results,"metrics",metrics,"currency","USD","comparisonBasis","Latest annual reporting period per company; fiscal dates may differ. Percentages are percentage points.");
        }
        String ticker=Ticker.normalize((String)a.get("ticker"));
        if(name.equals("get_company"))return CompanyResponse.from(companies.getCompany(ticker));
        if(name.equals("get_valuation_metrics"))return Map.of("ticker",ticker,"data",valuations.getValuation(ticker),"source","EquityLens existing quote data and SEC TTM calculations");
        if(name.equals("get_market_snapshot"))return snapshot(market.getMarket(ticker,null,null));
        if(name.equals("get_price_history")) {
            String frame=(String)a.get("timeframe");
            if(frame.equals("hourly"))return Map.of("ticker",ticker,"timeframe",frame,"status","UNSUPPORTED_CAPABILITY","message","EquityLens stores daily observations; hourly candles are unavailable and are not synthesized");
            var data=market.getMarket(ticker,date(a,"start"),date(a,"end"),MarketDataProvider.Interval.valueOf(frame.toUpperCase(Locale.ROOT)));
            var history=data.history();
            var candles=history.subList(Math.max(0,history.size()-366),history.size()).stream().map(row->{var candle=map(row);candle.put("timestamp",candle.remove("date"));return candle;}).toList();
            return Map.of("ticker",ticker,"timeframe",frame,"candles",candles,"metadata",snapshot(data),"truncated",history.size()>366,"limit",366);
        }
        boolean annual=!string(a,"type","annual").equals("quarterly"); if(annual&&limit(a)>10)throw new IllegalArgumentException();
        var dashboard=dashboards.getDashboard(ticker,annual?10:40); var data=annual?dashboard.annual():dashboard.quarterly();
        var filtered=rows(data).stream().filter(row->matches(row,a)).toList();
        var shown=filtered.subList(Math.max(0,filtered.size()-limit(a)),filtered.size());
        List<Map<String,Object>> projected=switch(name) {
            case "get_financial_history" -> shown.stream().map(row->{var r=select(row,List.of());r.put("value",row.get((String)a.get("metric")));return r;}).toList();
            case "get_growth_metrics" -> shown.stream().map(row->select(row,List.of("revenueGrowth"))).toList();
            case "get_profitability_metrics" -> shown.stream().map(row->select(row,List.of("grossMargin","operatingMargin","netMargin"))).toList();
            default -> shown;
        };
        var response=new LinkedHashMap<String,Object>(); response.put("ticker",ticker);response.put("type",annual?"annual":"quarterly");
        response.put("status",shown.isEmpty()?"EMPTY":"OK");response.put("periods",projected);response.put("currency","USD");response.put("percentageUnit","percentage_points");
        response.put("source","EquityLens normalized SEC financial data and existing dashboard calculations");
        response.put("window","Up to 10 annual or 40 quarterly periods in the bounded 10-year observation window");response.put("truncated",filtered.size()>shown.size());
        if(a.containsKey("metric"))response.put("metric",a.get("metric"));
        if(name.equals("get_growth_metrics"))response.put("unavailableMetrics",List.of("earningsGrowth","freeCashFlowGrowth"));
        if(!shown.isEmpty()) {
            LocalDate from=LocalDate.parse((String)shown.getFirst().get("periodEnd")).minusYears(1),to=LocalDate.parse((String)shown.getLast().get("periodEnd"));
            var sources=financials.getResearchSources(ticker,from,to);response.put("sourceObservations",sources.subList(0,Math.min(100,sources.size())));
            response.put("sourceObservationsTruncated",sources.size()>100);
            response.put("sourceNote","Reporting-window observations including preceding derivation/growth context, not one-to-one attribution of calculated output. No accession URLs are stored.");
        }
        return response;
    }
    private Map<String,Object> snapshot(MarketHistoryResponse r){var result=map(r);result.remove("history");return result;}
    private List<Map<String,Object>> rows(DashboardResponse.PeriodData data) {
        var result=new ArrayList<Map<String,Object>>();
        for(var income:data.income()) {
            var row=map(income);
            for(var list:List.of(data.cashFlow(),data.growth(),data.profitability(),data.balances()))for(Object item:list) {
                var other=map(item);
                if(Objects.equals(other.get("period"),row.get("period"))&&Objects.equals(other.get("periodEnd"),row.get("periodEnd"))
                        &&(!other.containsKey("periodStart")||Objects.equals(other.get("periodStart"),row.get("periodStart"))))row.putAll(other);
            }
            result.add(row);
        }
        return result;
    }
    private static boolean matches(Map<String,Object>row,Map<String,Object>a) {
        LocalDate end=LocalDate.parse((String)row.get("periodEnd")),from=date(a,"start"),to=date(a,"end");
        if(from!=null&&end.isBefore(from)||to!=null&&end.isAfter(to))return false;
        String p=string(a,"period","");return p.isEmpty()||p.equals(String.valueOf(end.getYear()))||p.equals(row.get("period")+String.valueOf(end.getYear()));
    }
    private static Map<String,Object> select(Map<String,Object>row,List<String>metrics) {
        var r=new LinkedHashMap<String,Object>();for(String key:List.of("period","periodStart","periodEnd","unit"))if(row.containsKey(key))r.put(key,row.get(key));
        for(String key:metrics)r.put(key,row.get(key));return r;
    }
    private Map<String,Object> map(Object value){return mapper.convertValue(value,new TypeReference<LinkedHashMap<String,Object>>(){});}
}
