package com.epam.reportportal.calculation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.TaskScheduler;

/**
 * Batch processing, grouping in batch based on amount or time unit.
 *
 * @param <T>
 */
public abstract class BatchProcessing<T> {

  private final TaskScheduler scheduler;
  private final List<T> objectList;
  private volatile ScheduledFuture<?> scheduledTask;
  private final int batchSize;
  private final long timeout;

  public BatchProcessing(int batchSize, long timeout, TaskScheduler scheduler) {
    if (timeout < 0 || scheduler == null) {
      throw new IllegalArgumentException(
          "Timeout must be greater than 0 and scheduler must be not null");
    }
    objectList = new ArrayList<>();
    this.batchSize = batchSize;
    this.timeout = timeout;
    this.scheduler = scheduler;
    this.scheduledTask = this.scheduler.schedule(this::processAndSchedule, getNextTime());
  }

  private Instant getNextTime() {
    return Instant.now().plusMillis(this.timeout);
  }

  public void add(T message) {
    synchronized (this) {
      this.objectList.add(message);
      if (this.objectList.size() >= this.batchSize) {
        processAndSchedule();
      }
    }
  }

  private void processAndSchedule() {
    List<T> copyObjectList;
    synchronized (this) {
      copyObjectList = new ArrayList<>(this.objectList);
      this.objectList.clear();
    }

    if (!copyObjectList.isEmpty()) {
      process(copyObjectList);
    }

    if (this.scheduledTask != null) {
      this.scheduledTask.cancel(true);
    }

    this.scheduledTask = this.scheduler.schedule(this::processAndSchedule, getNextTime());
  }

  protected abstract void process(List<T> objectList);
}
