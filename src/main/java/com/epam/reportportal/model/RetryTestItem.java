package com.epam.reportportal.model;

import java.time.LocalDateTime;

public class RetryTestItem {
    private final Long itemId;
    private final LocalDateTime startTime;
    private final String path;
    private final Long retryParentId;
    private final Long parentId;

    public RetryTestItem(Long itemId, LocalDateTime startTime, String path, Long retryParentId, Long parentId) {
        this.itemId = itemId;
        this.startTime = startTime;
        this.path = path;
        this.retryParentId = retryParentId;
        this.parentId = parentId;
    }

    public Long getItemId() {
        return itemId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public String getPath() {
        return path;
    }

    public Long getParentId() {
        return parentId;
    }

    public Long getRetryParentId() {
        return retryParentId;
    }
}