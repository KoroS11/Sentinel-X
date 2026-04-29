package com.sentinelx.common.dto;

import java.time.Instant;

public record DbHealthResult(
    String status,
    boolean dbReachable,
    long dbLatencyMs,
    String message,
    Instant checkedAt,
    String poolName,
    int activeConnections,
    int idleConnections,
    int totalConnections,
    int pendingThreads
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String status;
        private boolean dbReachable;
        private long dbLatencyMs;
        private String message;
        private Instant checkedAt;
        private String poolName;
        private int activeConnections;
        private int idleConnections;
        private int totalConnections;
        private int pendingThreads;

        private Builder() {}

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder dbReachable(boolean dbReachable) {
            this.dbReachable = dbReachable;
            return this;
        }

        public Builder dbLatencyMs(long dbLatencyMs) {
            this.dbLatencyMs = dbLatencyMs;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder checkedAt(Instant checkedAt) {
            this.checkedAt = checkedAt;
            return this;
        }

        public Builder poolName(String poolName) {
            this.poolName = poolName;
            return this;
        }

        public Builder activeConnections(int activeConnections) {
            this.activeConnections = activeConnections;
            return this;
        }

        public Builder idleConnections(int idleConnections) {
            this.idleConnections = idleConnections;
            return this;
        }

        public Builder totalConnections(int totalConnections) {
            this.totalConnections = totalConnections;
            return this;
        }

        public Builder pendingThreads(int pendingThreads) {
            this.pendingThreads = pendingThreads;
            return this;
        }

        public DbHealthResult build() {
            return new DbHealthResult(
                    status,
                    dbReachable,
                    dbLatencyMs,
                    message,
                    checkedAt,
                    poolName,
                    activeConnections,
                    idleConnections,
                    totalConnections,
                    pendingThreads
            );
        }
    }
}
