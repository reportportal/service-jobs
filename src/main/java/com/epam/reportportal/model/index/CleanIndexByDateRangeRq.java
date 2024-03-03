package com.epam.reportportal.model.index;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class CleanIndexByDateRangeRq {

  @JsonProperty("project")
  private Long projectId;

  @JsonProperty("interval_start_date")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime intervalStartDate;

  @JsonProperty("interval_end_date")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime intervalEndDate;

  public CleanIndexByDateRangeRq() {
  }

  public CleanIndexByDateRangeRq(Long projectId, LocalDateTime intervalStartDate,
      LocalDateTime intervalEndDate) {
    this.projectId = projectId;
    this.intervalStartDate = intervalStartDate;
    this.intervalEndDate = intervalEndDate;
  }

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long projectId) {
    this.projectId = projectId;
  }

  public LocalDateTime getIntervalStartDate() {
    return intervalStartDate;
  }

  public void setIntervalStartDate(LocalDateTime intervalStartDate) {
    this.intervalStartDate = intervalStartDate;
  }

  public LocalDateTime getIntervalEndDate() {
    return intervalEndDate;
  }

  public void setIntervalEndDate(LocalDateTime intervalEndDate) {
    this.intervalEndDate = intervalEndDate;
  }
}
