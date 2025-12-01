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
 * Cleans up old records from the test_item_deleted table. This job deletes records that are older
 * than the configured retention period to prevent the table from growing.
 *
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanDeletedTestItemsJob extends BaseJob {

  private static final String DELETE_OLD_RECORDS_QUERY = """
      DELETE FROM test_item_deleted
      WHERE deletion_date < ?::TIMESTAMP
      """;

  @Value("${rp.environment.variable.clean.deletedTestItems.retentionPeriod:12}")
  private Long retentionPeriodHours;

  public CleanDeletedTestItemsJob(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /**
   * Executes the cleanup job to remove old records from test_item_deleted table. Deletes all
   * records that are older than the configured retention period.
   */
  @Override
  @Scheduled(cron = "${rp.environment.variable.clean.deletedTestItems.cron:0 0 * * * *}")
  @SchedulerLock(name = "cleanDeletedTestItems", lockAtMostFor = "1h")
  public void execute() {
    cleanOldDeletedTestItems();
  }

  /**
   * Removes test item deletion records older than the configured retention period. This helps
   * maintain the test_item_deleted table at a manageable size.
   */
  private void cleanOldDeletedTestItems() {
    LocalDateTime cutoffDate = LocalDateTime.now(ZoneOffset.UTC).minusHours(retentionPeriodHours);
    int deletedCount = jdbcTemplate.update(DELETE_OLD_RECORDS_QUERY, cutoffDate);
    LOGGER.debug(
        "Deleted {} old records from test_item_deleted table older than {} hours, cutoff date: {}",
        deletedCount, retentionPeriodHours, cutoffDate
    );
  }
}

