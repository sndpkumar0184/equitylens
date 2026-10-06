package com.equitylens.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LocalLLMProviderTest {
    @Test void nativeOllamaToolSelectionAndToolResultsRoundTripWithoutCloudDependencies()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var mapper=new ObjectMapper();
        server.createContext("/api/chat",exchange->{
            var body=mapper.readTree(exchange.getRequestBody().readAllBytes());
            assertEquals("fixture-local",body.path("model").asText());assertFalse(body.path("stream").asBoolean());
            assertEquals("get_company",body.path("tools").get(0).path("function").path("name").asText());
            assertEquals("get_company",body.path("messages").get(1).path("tool_name").asText());
            byte[] bytes="{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"get_company\",\"arguments\":{\"ticker\":\"META\"}}}]}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var provider=new LocalLLMProvider(mapper,"http://127.0.0.1:"+server.getAddress().getPort(),"fixture-local");
            var result=provider.complete(List.of(AIProvider.Message.text("user","Who is META?"),new AIProvider.Message("tool","{}",List.of(),"get_company")),
                    List.of(new AIProvider.ToolDefinition("get_company","Profile",Map.of("type","object"))));
            assertEquals("META",result.toolCalls().getFirst().arguments().get("ticker"));
        }finally{server.stop(0);}
    }
    @Test void unavailableRuntimeReturnsActionableErrorWithoutLeakingUrl() {
        var provider=new LocalLLMProvider(new ObjectMapper(),"http://127.0.0.1:1","missing-model");
        var error=assertThrows(ResponseStatusException.class,()->provider.complete(List.of(AIProvider.Message.text("user","test")),List.of()));
        assertEquals(503,error.getStatusCode().value());assertTrue(error.getReason().contains("Start Ollama"));assertFalse(error.getReason().contains("127.0.0.1"));
    }

    @Test void localTemplateJsonEnvelopeCanSelectOnlyDiscoveredTools()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var mapper=new ObjectMapper();var answer=new java.util.concurrent.atomic.AtomicReference<>("{\"name\":\"get_company\",\"arguments\":{\"ticker\":\"META\"}}");
        server.createContext("/api/chat",exchange->{
            exchange.getRequestBody().readAllBytes();
            byte[] bytes=mapper.writeValueAsBytes(Map.of("message",Map.of("role","assistant","content",answer.get())));
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var provider=new LocalLLMProvider(mapper,"http://127.0.0.1:"+server.getAddress().getPort(),"fixture-local");
            var messages=List.of(AIProvider.Message.text("user","Profile?"));
            var definitions=List.of(new AIProvider.ToolDefinition("get_company","Profile",Map.of("type","object")));
            assertEquals("get_company",provider.complete(messages,definitions).toolCalls().getFirst().name());
            answer.set("{\"name\":\"execute_sql\",\"arguments\":{\"sql\":\"drop\"}}");
            assertTrue(provider.complete(messages,definitions).toolCalls().isEmpty());
            answer.set("Please run {\"name\":\"get_company\",\"arguments\":{}}");
            assertTrue(provider.complete(messages,definitions).toolCalls().isEmpty());
        }finally{server.stop(0);}
    }
}
