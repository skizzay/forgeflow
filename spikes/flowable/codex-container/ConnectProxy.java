import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.Executors;

/** Credential-free CONNECT-only proxy. No interception, request logging or arbitrary destinations. */
public class ConnectProxy {
    public static void main(String[] args) throws Exception {
        var workers = Executors.newVirtualThreadPerTaskExecutor();
        try (var listener = new ServerSocket(3128)) {
            while (true) {
                Socket client = listener.accept();
                workers.submit(() -> {
                    try (client) {
                        client.setSoTimeout(900_000);
                        var input = client.getInputStream();
                        var header = new StringBuilder();
                        while (header.length() <= 8192 && !header.toString().endsWith("\r\n\r\n")) {
                            int value = input.read();
                            if (value < 0) return;
                            header.append((char) value);
                        }
                        String[] line = header.toString().split("\r\n", 2)[0].split(" ");
                        String host = line.length == 3 && line[0].equals("CONNECT") && line[1].endsWith(":443")
                                ? line[1].substring(0, line[1].length() - 4) : "";
                        if (header.length() > 8192 || !Set.of("chatgpt.com", "api.openai.com", "auth.openai.com").contains(host)) {
                            client.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                            return;
                        }
                        try (var remote = new Socket()) {
                            remote.connect(new InetSocketAddress(host, 443), 10_000);
                            remote.setSoTimeout(900_000);
                            client.getOutputStream().write("HTTP/1.1 200 Connection established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                            client.getOutputStream().flush();
                            var upstream = workers.submit(() -> {
                                try { input.transferTo(remote.getOutputStream()); }
                                catch (Exception ignored) { }
                            });
                            try { remote.getInputStream().transferTo(client.getOutputStream()); }
                            finally { upstream.cancel(true); }
                        }
                    } catch (Exception ignored) {
                        // Never log request headers, tokens or upstream response bodies.
                    }
                });
            }
        }
    }
}
