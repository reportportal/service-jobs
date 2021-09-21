package com.epam.reportportal.calculation;

import com.epam.reportportal.calculation.statistic.RetryCalculation;
import com.epam.reportportal.model.RetryTestItem;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Class wrapper for retrying batching. Calculation implemented in separated class.
 */
public class RetryProcessing extends BatchProcessing<RetryTestItem> {

    private final RetryCalculation retryCalculation;

    public RetryProcessing(int batchSize, long timeout, TaskScheduler scheduler, RetryCalculation retryCalculation) {
        super(batchSize, timeout, scheduler);
        this.retryCalculation = retryCalculation;
    }

    @Override
    protected void process(List<RetryTestItem> retryTestItemList) {
        // possible logs
        if (!CollectionUtils.isEmpty(retryTestItemList)) {
            retryCalculation.handleRetries(retryTestItemList);
        }
    }
}
