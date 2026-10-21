package io.forgeflow.spike;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import org.springframework.dao.EmptyResultDataAccessException;

/** Narrow experimental HTTPS API. No workflow upload, DB, engine REST or deployment endpoints. */
public final class HandoffApi implements AutoCloseable {
    public record Identity(String subject, String token, boolean operator, Set<String> capabilities) {}
    public record Request(Long fence, String effectId) {}
    private final HttpsServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ObjectMapper json = new ObjectMapper();

    public HandoffApi(SSLContext tls, List<Identity> identities, ApprovedHandoff gate, RecoveryJobs jobs) throws IOException {
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            int code = 200;
            Object result;
            try {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                var identity = identities.stream().filter(candidate -> auth != null && MessageDigest.isEqual(
                        auth.getBytes(StandardCharsets.UTF_8), ("Bearer " + candidate.token()).getBytes(StandardCharsets.UTF_8)))
                        .findFirst().orElse(null);
                if (identity == null) {
                    code = 401;
                    result = Map.of("error", "unauthorized");
                } else {
                    String[] path = exchange.getRequestURI().getPath().split("/");
                    if (!exchange.getRequestMethod().equals("POST") || path.length != 5
                            || !path[1].equals("v1") || !path[2].equals("jobs")) {
                        code = 404;
                        result = Map.of("error", "unknown endpoint");
                    } else {
                        byte[] body = exchange.getRequestBody().readNBytes(4097);
                        if (body.length > 4096) throw new IllegalArgumentException("Oversized request");
                        Request request = json.readValue(body, Request.class);
                        String key = java.util.UUID.fromString(path[3]).toString();
                        String action = path[4];
                        if (action.equals("approve")) {
                            if (!identity.operator()) throw new SecurityException("Operator required");
                            gate.approve(key, identity.subject());
                            result = Map.of("status", "approved");
                        } else if (identity.operator()) {
                            throw new SecurityException("Worker identity required");
                        } else if (action.equals("claim")) {
                            result = gate.claim(key, identity.subject(), identity.capabilities());
                        } else {
                            if (request.fence() == null) throw new IllegalArgumentException("Fence required");
                            var lease = new RecoveryJobs.Lease(key, request.fence(), identity.subject());
                            result = switch (action) {
                                case "heartbeat" -> { gate.heartbeat(lease); yield Map.of("status", "renewed"); }
                                case "complete" -> Map.of("status", gate.complete(lease, request.effectId()).name());
                                case "cancel" -> Map.of("cancelled", jobs.cancel(lease));
                                default -> { code = 404; yield Map.of("error", "unknown endpoint"); }
                            };
                        }
                    }
                }
            } catch (SecurityException forbidden) {
                code = 403; result = Map.of("error", "forbidden");
            } catch (IllegalStateException conflict) {
                code = 409; result = Map.of("error", "execution conflict");
            } catch (EmptyResultDataAccessException missing) {
                code = 404; result = Map.of("error", "unknown job");
            } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException invalid) {
                code = 400; result = Map.of("error", "invalid request");
            } catch (Exception failure) {
                code = 500; result = Map.of("error", "internal failure");
            }
            byte[] bytes = json.writeValueAsBytes(result);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
            exchange.close();
        });
        server.start();
    }

    public int port() { return server.getAddress().getPort(); }

    @Override public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
