package com.airtribe.relayflow.worker.excutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.airtribe.relayflow.common.exception.NodeExecutionException;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DeterministicNodeExecutorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serverErrorIsClassifiedAsTransient() throws IOException {
        HttpServer server = serverReturning(503);
        try {
            NodeExecutionException error = assertThrows(NodeExecutionException.class,
                () -> execute(server.getAddress().getPort()));
            assertTrue(error.isRetryable());
            assertEquals(503, error.getHttpStatus());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void clientErrorIsClassifiedAsPermanent() throws IOException {
        HttpServer server = serverReturning(400);
        try {
            NodeExecutionException error = assertThrows(NodeExecutionException.class,
                () -> execute(server.getAddress().getPort()));
            assertFalse(error.isRetryable());
            assertEquals(400, error.getHttpStatus());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refundSendsAmountAndStableIdempotencyKeyAndReturnsWorldOutput() throws IOException {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        HttpServer server = serverCapturing(path, requestBody, idempotencyKey,
            "{\"status\":\"refunded\",\"reference_id\":\"ref_123\",\"amount_usd\":25}");
        try {
            DeterministicNodeExecutor executor = executor(server);
            JsonNode result = executor.execute("run_1", 3, orderNode("refund", "notify"),
                mapper.readTree("{\"action\":\"refund\",\"order_id\":\"ord_7\",\"amount_usd\":25}"))
                .output();

            assertEquals("/orders/ord_7/refund", path.get());
            assertEquals("{\"amount_usd\":25}", requestBody.get());
            assertEquals("run_1:refund:3", idempotencyKey.get());
            assertEquals("refunded", result.path("status").asString());
            assertEquals("ref_123", result.path("reference_id").asString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fullRefundOmitsAmountFromRequest() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = serverCapturing(new AtomicReference<>(), requestBody,
            new AtomicReference<>(), "{\"status\":\"refunded\",\"reference_id\":\"ref_123\"}");
        try {
            executor(server).execute("run_1", 1, orderNode("refund", null),
                mapper.readTree("{\"action\":\"refund\",\"order_id\":\"ord_7\"}"));

            assertEquals("{}", requestBody.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void replacementUsesReplacementEndpointAndEmptyBody() throws IOException {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = serverCapturing(path, requestBody, new AtomicReference<>(),
            "{\"status\":\"replacement_created\",\"reference_id\":\"rpl_123\",\"order_id\":\"ord_7\"}");
        try {
            JsonNode result = executor(server).execute("run_1", 2, orderNode("replace", null),
                mapper.readTree("{\"action\":\"replacement\",\"order_id\":\"ord_7\"}")).output();

            assertEquals("/orders/ord_7/replacement", path.get());
            assertEquals("{}", requestBody.get());
            assertEquals("replacement_created", result.path("status").asString());
            assertEquals("rpl_123", result.path("reference_id").asString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unsupportedOrderActionFailsBeforeCallingWorld() throws IOException {
        HttpServer server = serverReturning(200);
        try {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> executor(server).execute("run_1", 1, orderNode("refund", null),
                    mapper.readTree("{\"action\":\"cancel\",\"order_id\":\"ord_7\"}")));
            assertTrue(error.getMessage().contains("Unsupported order_action"));
        } finally {
            server.stop(0);
        }
    }

    private void execute(int port) {
        DeterministicNodeExecutor executor = new DeterministicNodeExecutor(mapper,
            "http://127.0.0.1:" + port);
        JsonNode node = mapper.readTree("""
            {"id":"request","type":"http_request","next":null}
            """);
        JsonNode params = mapper.readTree("""
            {"method":"POST","url":"http://127.0.0.1:%d/failure","body":{"test":true}}
            """.formatted(port));
        executor.execute("run_1", 1, node, params);
    }

    private HttpServer serverReturning(int status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/failure", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer serverCapturing(AtomicReference<String> path, AtomicReference<String> requestBody,
            AtomicReference<String> idempotencyKey, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getRawPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private DeterministicNodeExecutor executor(HttpServer server) {
        return new DeterministicNodeExecutor(mapper,
            "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private JsonNode orderNode(String id, String next) {
        String nextNode = next == null ? "null" : "\"" + next + "\"";
        return mapper.readTree("""
            {"id":"%s","type":"order_action","next":%s}
            """.formatted(id, nextNode));
    }
}
