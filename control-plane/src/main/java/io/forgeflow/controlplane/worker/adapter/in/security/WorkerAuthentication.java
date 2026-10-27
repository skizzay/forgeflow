package io.forgeflow.controlplane.worker.adapter.in.security;

import io.forgeflow.controlplane.worker.domain.WorkerPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/** Normalizes an already validated JWT; token verification belongs to Spring Security. */
public final class WorkerAuthentication {
    private WorkerAuthentication() { }

    public static UsernamePasswordAuthenticationToken map(Jwt jwt, String subject, UUID workerId) {
        if (jwt.getExpiresAt() == null || !subject.equals(jwt.getSubject())) {
            throw new BadCredentialsException("Worker identity or required expiry is missing");
        }
        var granted = new JwtGrantedAuthoritiesConverter().convert(jwt);
        var workerScope = new SimpleGrantedAuthority("SCOPE_forgeflow.worker");
        return UsernamePasswordAuthenticationToken.authenticated(new WorkerPrincipal(workerId), null,
                granted.contains(workerScope) ? List.of(workerScope) : List.of());
    }
}
