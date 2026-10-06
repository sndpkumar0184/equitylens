package com.equitylens.ai;

import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/** Creates a real SDK connection lazily, after the backend's HTTP server is running. */
@Component
public class EquityLensMcpClient {
    private final String baseUrl,token;
    public EquityLensMcpClient(@Value("${equitylens.ai.mcp.url:http://127.0.0.1:8080}")String baseUrl,
            @Value("${equitylens.ai.access-token:}")String token) {
        URI uri=URI.create(baseUrl);
        if(!"http".equals(uri.getScheme())||!java.util.Set.of("localhost","127.0.0.1","::1","[::1]").contains(uri.getHost()))
            throw new IllegalArgumentException("Assistant MCP target must be the local EquityLens backend");
        this.baseUrl=baseUrl;this.token=token;
    }
    public McpSyncClient connect() {
        var request=HttpRequest.newBuilder();if(!token.isBlank())request.header("Authorization","Bearer "+token);
        var transport=HttpClientStreamableHttpTransport.builder(baseUrl).endpoint("/mcp").requestBuilder(request)
                .openConnectionOnStartup(false).connectTimeout(Duration.ofSeconds(5)).maxResponseSize(262144).build();
        var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(120)).clientInfo(new Implementation("equitylens-assistant","1.0.0")).build();
        try{client.initialize();return client;}catch(RuntimeException ex){client.close();throw ex;}
    }
}
