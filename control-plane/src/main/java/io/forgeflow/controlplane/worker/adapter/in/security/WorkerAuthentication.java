package io.forgeflow.controlplane.worker.adapter.in.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

/** HTTP credential verification; authenticated principal is always the configured worker UUID. */
public final class WorkerAuthentication {
    private WorkerAuthentication() { }
    public static AuthenticationWebFilter create(UUID workerId, String secret) {
        byte[] expected = digest(secret);
        var unauthorized = unauthorized();
        ReactiveAuthenticationManager manager = candidate -> {
            if (!MessageDigest.isEqual(expected, digest((String) candidate.getCredentials()))) {
                return Mono.error(new BadCredentialsException("Invalid worker credential"));
            }
            return Mono.just(UsernamePasswordAuthenticationToken.authenticated(workerId, null,
                    List.of(new SimpleGrantedAuthority("ROLE_WORKER"))));
        };
        var authentication = new AuthenticationWebFilter(manager);
        authentication.setServerAuthenticationConverter(exchange -> {
            var values = exchange.getRequest().getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION);
            if (values.isEmpty()) return Mono.empty();
            if (values.size() != 1 || !values.getFirst().regionMatches(true, 0, "Bearer ", 0, 7)) {
                return Mono.error(new BadCredentialsException("Invalid worker credential"));
            }
            String token = values.getFirst().substring(7);
            if (!token.matches("[A-Za-z0-9_-]{32,256}")) {
                return Mono.error(new BadCredentialsException("Invalid worker credential"));
            }
            return Mono.just(UsernamePasswordAuthenticationToken.unauthenticated("configured-worker", token));
        });
        authentication.setAuthenticationFailureHandler(new ServerAuthenticationEntryPointFailureHandler(unauthorized));
        authentication.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        return authentication;
    }
    public static ServerAuthenticationEntryPoint unauthorized() {
        return (exchange, failure) -> {
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            byte[] body = "{\"commandId\":null,\"code\":\"UNAUTHENTICATED\",\"action\":\"STOP\"}".getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        };
    }
    private static byte[] digest(String secret) {
        try { return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 unavailable", unavailable); }
    }
}
