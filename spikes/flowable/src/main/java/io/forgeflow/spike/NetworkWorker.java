package io.forgeflow.spike;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Separate-JVM deterministic worker: HTTPS only; no engine or database access. */
public final class NetworkWorker {
    public static void main(String[] args) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var json = new ObjectMapper();
        String base = System.getenv("SPIKE_API_URL");
        String token = System.getenv("SPIKE_WORKER_TOKEN");
        var claimed = json.readTree(send(client, base + "/claim", token, "{}"));
        long fence = claimed.path("lease").path("fence").asLong();
        if (fence <= 0 || !claimed.path("activity").asText().equals("coding-test.v1")) {
            throw new IllegalStateException("Unsupported contract");
        }
        String evidence = switch (claimed.path("provider").asText()) {
            case "deterministic-a" -> "fixture-a";
            case "deterministic-b" -> "fixture-b";
            default -> throw new IllegalStateException("Unsupported provider");
        };
        send(client, base + "/heartbeat", token, "{\"fence\":" + fence + "}");
        if (args.length == 1 && args[0].equals("crash-after-heartbeat")) {
            Runtime.getRuntime().halt(75); // explicit prototype fault injection, before provider result
        }
        send(client, base + "/complete", token, json.writeValueAsString(java.util.Map.of("fence", fence, "effectId", evidence)));
    }

    private static String send(HttpClient client, String url, String token, String body) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("API rejected operation: " + response.statusCode());
        return response.body();
    }
}
