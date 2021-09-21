package com.epam.reportportal.calculation.statistic;

import com.epam.reportportal.model.RetryTestItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;

import java.util.List;

import static java.util.stream.Collectors.groupingBy;

public class RetryCalculation {
    private final JdbcTemplate jdbcTemplate;

    public RetryCalculation(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void handleRetries(List<RetryTestItem> retries) {
        if (CollectionUtils.isEmpty(retries)) return;

        var retriesGroupedByParent = retries.stream().collect(groupingBy(RetryTestItem::getParentId));
        retriesGroupedByParent.forEach((k, itemRetries) -> handleItemRetries(itemRetries));
    }

    private void handleItemRetries(List<RetryTestItem> retries) {
    }
}