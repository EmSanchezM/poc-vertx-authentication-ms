package com.auth.microservice.application.query;

import com.auth.microservice.common.cqrs.Query;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Query to get administrative reports and metrics.
 * Provides system statistics and insights for administrators.
 */
public class GetAdminReportsQuery extends Query {
    private final String reportType;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final boolean includeDetails;

    public GetAdminReportsQuery(String userId, String reportType, LocalDateTime startDate, 
                               LocalDateTime endDate, boolean includeDetails) {
        super(userId);
        this.reportType = Objects.requireNonNull(reportType, "Report type cannot be null");
        this.startDate = startDate;
        this.endDate = endDate;
        this.includeDetails = includeDetails;
    }

    public GetAdminReportsQuery(String userId, String reportType) {
        this(userId, reportType, null, null, false);
    }

    public String getReportType() {
        return reportType;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public boolean isIncludeDetails() {
        return includeDetails;
    }

    @Override
    public String toString() {
        return String.format("GetAdminReportsQuery{reportType='%s', startDate=%s, endDate=%s, includeDetails=%s, %s}", 
            reportType, startDate, endDate, includeDetails, super.toString());
    }
}