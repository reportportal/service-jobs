/*
 * Copyright 2024 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.jobs.BaseJob;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cleans up old records from the launches_modified table. This job deletes records that are older
 * than the configured retention period so the table does not grow without bound after Logstash (or
 * similar) has consumed the change markers.
 *
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanLaunchesModifiedJob extends BaseJob {

  private static final String DELETE_OLD_RECORDS_QUERY = """
      DELETE FROM launches_modified
      WHERE created_at < ?::TIMESTAMP
      """;

  @Value("${rp.environment.variable.clean.launchesModified.retentionPeriod:24}")
  private Long retentionPeriodHours;

  public CleanLaunchesModifiedJob(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /**
   * Executes the cleanup job to remove old records from launches_modified. Deletes all records
   * older than the configured retention period.
   */
  @Override
  @Scheduled(cron = "${rp.environment.variable.clean.launchesModified.cron:0 0 * * * *}")
  @SchedulerLock(name = "cleanLaunchesModified", lockAtMostFor = "1h")
  public void execute() {
    cleanOldLaunchesModified();
  }

  /**
   * Removes launch modification markers older than the configured retention period.
   */
  private void cleanOldLaunchesModified() {
    LocalDateTime cutoffDate = LocalDateTime.now(ZoneOffset.UTC).minusHours(retentionPeriodHours);
    int deletedCount = jdbcTemplate.update(DELETE_OLD_RECORDS_QUERY, cutoffDate);
    LOGGER.info(
        "Deleted {} 'launches_modified' records older than {} hours, cutoff date: {}",
        deletedCount, retentionPeriodHours, cutoffDate
    );
  }
}
