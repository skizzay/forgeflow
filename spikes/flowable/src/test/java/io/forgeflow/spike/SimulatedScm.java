package io.forgeflow.spike;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Separate durable fixture, deliberately allowing duplicate PRs to expose blind retries. */
final class SimulatedScm {
    static String create(Path directory, String jobKey) throws IOException {
        String id = UUID.randomUUID().toString();
        Files.writeString(directory.resolve(id + ".effect"), jobKey);
        return id;
    }

    static RecoveryJobs.Observation lookup(Path directory, String key) throws IOException {
        try (var files = Files.list(directory)) {
            var matches = files.filter(path -> {
                try {
                    return path.getFileName().toString().endsWith(".effect") && Files.readString(path).equals(key);
                } catch (IOException error) {
                    throw new java.io.UncheckedIOException(error);
                }
            }).toList();
            if (matches.size() == 1) {
                String name = matches.getFirst().getFileName().toString();
                return new RecoveryJobs.Observation(RecoveryJobs.Finding.FOUND, name.substring(0, name.length() - 7));
            }
            return new RecoveryJobs.Observation(matches.isEmpty()
                    ? RecoveryJobs.Finding.ABSENT : RecoveryJobs.Finding.UNKNOWN, null);
        }
    }
}
