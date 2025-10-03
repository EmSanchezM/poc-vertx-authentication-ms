package com.auth.microservice.application.result;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Result of admin reports query.
 * Contains system metrics and statistics for administrative purposes.
 */
public class AdminReportsResult {
    private final String reportType;
    private final LocalDateTime generatedAt;
    private final Map<String, Object> metrics;
    private final Map<String, Object> details;

    public AdminReportsResult(String reportType, Map<String, Object> metrics, Map<String, Object> details) {
        this.reportType = reportType;
        this.generatedAt = LocalDateTime.now();
        this.metrics = metrics;
        this.details = details;
    }

    public AdminReportsResult(String reportType, Map<String, Object> metrics) {
        this(reportType, metrics, null);
    }

    public String getReportType() {
        return reportType;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return String.format("AdminReportsResult{reportType='%s', generatedAt=%s, metricsCount=%d}", 
            reportType, generatedAt, metrics != null ? metrics.size() : 0);
    }
}