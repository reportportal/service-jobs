package com.epam.reportportal.jobs.calculation;

import com.epam.reportportal.calculation.RetryProcessing;
import com.epam.reportportal.model.RetryTestItem;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

//@Service
// commented template for mechanism for possible retry processing
public class RetryCalculationJob {
    private final RetryProcessing retryProcessing;

    public RetryCalculationJob(RetryProcessing retryProcessing) {
        this.retryProcessing = retryProcessing;
    }

//    @RabbitListener(queues = "${rp.variable.calculation.retry.queue}")
    public void calculate(RetryTestItem retryItem) {
        retryProcessing.add(retryItem);
    }
}