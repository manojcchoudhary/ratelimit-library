package com.lycosoft.ratelimit.storage;

import com.lycosoft.ratelimit.spi.StorageProvider;
import java.util.Map;

/**
 * Simple health checker that works without Spring Boot.
 */
public class StorageHealthChecker {

    private final StorageProvider storageProvider;

    public StorageHealthChecker(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    /**
     * Performs a health check.
     *
     * @return health check result
     */
    public HealthCheckResult check() {
        try {
            Map<String, Object> diagnostics = storageProvider.getDiagnostics();
            boolean healthy = storageProvider.isHealthy();

            return new HealthCheckResult(
                    healthy ? Status.UP : Status.DOWN,
                    diagnostics
            );
        } catch (Exception e) {
            return new HealthCheckResult(
                    Status.DOWN,
                    Map.of("error", e.getMessage())
            );
        }
    }

    public enum Status {
        UP, DOWN
    }

    public static class HealthCheckResult {
        private final Status status;
        private final Map<String, Object> details;

        public HealthCheckResult(Status status, Map<String, Object> details) {
            this.status = status;
            this.details = details;
        }

        public Status getStatus() { return status; }
        public Map<String, Object> getDetails() { return details; }
        public boolean isHealthy() { return status == Status.UP; }
    }
}