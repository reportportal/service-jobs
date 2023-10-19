/*
 * Copyright 2023 EPAM Systems
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
import com.epam.reportportal.utils.ValidationUtil;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Deleting Events by retention policy.
 *
 * @author <a href="mailto:andrei_piankouski@epam.com">Andrei Piankouski</a>
 */
@Service
@ConditionalOnProperty(prefix = "rp.environment.variable",
    name = "clean.events.retentionPeriod")
public class EventsRetentionPolicyJob extends BaseJob {

  private static final String DELETE_ACTIVITY_BY_RETENTION =
      "DELETE FROM activity "
          + "WHERE created_at < NOW() - (:retentionPeriod * interval '1 day')";

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @Value("${rp.environment.variable.clean.events.retentionPeriod}")
  private Long retentionPeriod;

  public EventsRetentionPolicyJob(JdbcTemplate jdbcTemplate,
      NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
    super(jdbcTemplate);
    this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
  }

  @Override
  @Scheduled(cron = "${rp.environment.variable.clean.events.cron}")
  @SchedulerLock(name = "eventsRetentionPolicy", lockAtMostFor = "24h")
  public void execute() {
    if (ValidationUtil.isInvalidRetentionPeriod(retentionPeriod)) {
      LOGGER.info("No events are deleted");
      return;
    }
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("retentionPeriod", retentionPeriod);
    int deletedRows = namedParameterJdbcTemplate.update(DELETE_ACTIVITY_BY_RETENTION, params);
    LOGGER.info("{} - events were deleted due to retention policy", deletedRows);
  }


}
