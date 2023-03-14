package com.epam.reportportal.calculation;

import java.util.List;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.CollectionUtils;

/**
 * Class for retrying batching, possible Object will be converted to some specific object
 */
public class RetryProcessing extends BatchProcessing<Object> {

  public RetryProcessing(int batchSize, long timeout, TaskScheduler scheduler) {
    super(batchSize, timeout, scheduler);
  }

  @Override
  protected void process(List<Object> objectList) {
    if (CollectionUtils.isEmpty(objectList)) {
      System.out.println("Collection is empty");
    } else {
      System.out.println("Processing...");
      objectList.forEach(System.out::println);
    }
  }
}
