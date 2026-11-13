/*
 * Copyright 2026 EPAM Systems
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
 * Cleans up expired restore-password links. This job deletes rows from restore_password_bid that are older than the
 * configured retention period.
 */
@Service
public class CleanRestorePasswordBidJob extends BaseJob {

  private static final String DELETE_OLD_RECORDS_QUERY = """
      DELETE FROM restore_password_bid
      WHERE last_modified < ?::TIMESTAMP
      """;

  // Kept in sync with rp.restore.password.bid.ttl (service-api).
  @Value("${rp.environment.variable.clean.restorePasswordBid.retentionPeriod}")
  private Long retentionPeriodHours;

  /**
   * Initializes {@link CleanRestorePasswordBidJob}.
   *
   * @param jdbcTemplate {@link JdbcTemplate}
   */
  public CleanRestorePasswordBidJob(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /**
   * Executes the cleanup job to remove expired restore-password links. Deletes all records older than the configured
   * retention period.
   */
  @Override
  @Scheduled(cron = "${rp.environment.variable.clean.restorePasswordBid.cron}")
  @SchedulerLock(name = "cleanRestorePasswordBid", lockAtMostFor = "1h")
  public void execute() {
    cleanOldRestorePasswordBids();
  }

  private void cleanOldRestorePasswordBids() {
    LocalDateTime cutoffDate = LocalDateTime.now(ZoneOffset.UTC).minusHours(retentionPeriodHours);
    int deletedCount = jdbcTemplate.update(DELETE_OLD_RECORDS_QUERY, cutoffDate);
    LOGGER.info("Deleted {} 'restore_password_bid' records older than {} hours, cutoff date: {}",
        deletedCount, retentionPeriodHours, cutoffDate);
  }
}
