package io.forgeflow.controlplane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ControlPlaneApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(ControlPlaneApplication.class, args);
        if (context.getEnvironment().matchesProfiles("migrate")) {
            context.close();
        }
    }
}
