package com.equitylens.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SecTickerLookupTest {
    private HttpServer server;
    private SecDataService sec;
    private final AtomicInteger requests = new AtomicInteger();
    private int status = 200;
    private String body = """
            {"0":{"ticker":"META","cik_str":1326801,"title":"Meta Platforms, Inc."},
             "1":{"ticker":"AAPL","cik_str":320193,"title":"Apple Inc."}}
            """;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            assertEquals("EquityLens test@example.com", exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        sec = new SecDataService(RestClient.builder(), new ObjectMapper(), "EquityLens test@example.com", base, base);
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void resolvesMetaAndAppleAndCachesDirectory() {
        assertEquals("0001326801", sec.resolveTicker(" meta ").cik());
        assertEquals("Apple Inc.", sec.resolveTicker("aapl").name());
        assertEquals("0000320193", sec.resolveTicker("AAPL").cik());
        assertEquals(1, requests.get());
    }

    @Test void unknownTickerIs404WithoutRedownloadingDirectory() {
        sec.resolveTicker("META");
        var error = assertThrows(ResponseStatusException.class, () -> sec.resolveTicker("UNKNOWN"));
        assertEquals(404, error.getStatusCode().value());
        assertEquals(1, requests.get());
    }

    @Test void invalidTickerIs400AndDoesNotCallSec() {
        for (String input : new String[]{"", " ", "A/PL", "toolongticker"}) {
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> sec.resolveTicker(input)).getStatusCode().value());
        }
        assertEquals(0, requests.get());
    }

    @Test void secFailureIsRetryableAndDoesNotBecomeUnknownTicker() {
        status = 429;
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> sec.resolveTicker("AAPL")).getStatusCode().value());
        status = 200;
        assertEquals("AAPL", sec.resolveTicker("AAPL").ticker());
        assertEquals(2, requests.get());
    }

    @Test void malformedDirectoryIsAnUpstreamError() {
        body = "{}";
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> sec.resolveTicker("AAPL")).getStatusCode().value());
    }
}
