package com.airtribe.relayflow.worker.excutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.airtribe.relayflow.worker.exception.NodeExecutionException;
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

    private void execute(int port) {
        DeterministicNodeExecutor executor = new DeterministicNodeExecutor(
            mapper, "http://127.0.0.1:" + port);
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
}
