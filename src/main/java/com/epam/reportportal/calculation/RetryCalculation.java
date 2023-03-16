package com.epam.reportportal.calculation;


import org.springframework.scheduling.concurrent.DefaultManagedTaskScheduler;
import org.springframework.stereotype.Component;

@Component
// example of usage
public class RetryCalculation {

  private final RetryProcessing retryProcessing;

  public RetryCalculation() {
    retryProcessing = new RetryProcessing(5, 3000, new DefaultManagedTaskScheduler());
  }

    /* example of using fill be removed during integration
    @RabbitListener(queues = "some_queue")
    public void calculate(Object retryItem) {
        retryProcessing.add(retryItem);
    }
     */
}
