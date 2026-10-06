package com.equitylens.mcp;

import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.*;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.*;
import java.util.Arrays;

@Configuration
@ConditionalOnProperty(name="equitylens.mcp.enabled",havingValue="true",matchIfMissing=true)
public class McpConfiguration {
    @Bean
    HttpServletStreamableServerTransportProvider mcpTransport(
            @Value("${equitylens.mcp.allowed-hosts:localhost:*,127.0.0.1:*}")String hosts,
            @Value("${equitylens.mcp.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")String origins) {
        return HttpServletStreamableServerTransportProvider.builder().mcpEndpoint("/mcp").maxRequestSize(65536)
                .securityValidator(DefaultServerTransportSecurityValidator.builder().allowedHosts(Arrays.asList(hosts.split(",")))
                        .allowedOrigins(Arrays.asList(origins.split(","))).build()).build();
    }
    @Bean
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(HttpServletStreamableServerTransportProvider transport) {
        var servlet=new ServletRegistrationBean<>(transport,"/mcp");servlet.setAsyncSupported(true);servlet.setLoadOnStartup(1);return servlet;
    }
    @Bean(destroyMethod="close")
    McpSyncServer mcpServer(HttpServletStreamableServerTransportProvider transport,ResearchTools tools) {
        return McpServer.sync(transport).serverInfo("equitylens","1.0.0")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .instructions("Read-only EquityLens research. Null means unavailable. Respect reporting dates, source status and truncation. No trading or mutation tools.")
                .tools(tools.specifications()).build();
    }
}
