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

package com.epam.reportportal.jobs.notification;

import static com.epam.reportportal.config.rabbit.InternalConfiguration.EXCHANGE_NOTIFICATION;
import static com.epam.reportportal.config.rabbit.InternalConfiguration.QUEUE_EMAIL;

import com.epam.reportportal.jobs.BaseJob;
import com.epam.reportportal.model.EmailNotificationRequest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Notify users of oncoming deletion according to retention policy.
 *
 * @author Andrei Piankouski
 */
@Service
public class NotifyUserExpirationJob extends BaseJob {

  private static final String EMAIL = "email";
  private static final String USER_EXPIRATION_TEMPLATE = "userExpirationNotification";
  private static final String DAYS = " days";
  private static final String INACTIVITY_PERIOD = "inactivityPeriod";
  private static final String REMAINING_TIME = "remainingTime";
  private static final String DEADLINE_DATE = "deadlineDate";

  private static final String SELECT_USERS_FOR_NOTIFY = "WITH user_last_action AS ( "
      + "SELECT "
      + "  u.id as user_id, "
      + "  u.email as email, "
      + "DATE_PART('day', NOW() - GREATEST("
      + "  to_timestamp(CAST(u.metadata->'metadata'->>'last_login' AS bigint) / 1000), "
      + "MAX(ak.last_used_at))) AS inactivityPeriod "
      + "FROM "
      + "  users u "
      + "  LEFT JOIN api_keys ak ON u.id = ak.user_id "
      + "WHERE "
      + "  u.role != 'ADMINISTRATOR' "
      + "GROUP BY "
      + "  u.id "
      + ") "
      + "SELECT "
      + "  user_last_action.user_id, "
      + "  user_last_action.email, "
      + "  user_last_action.inactivityPeriod, "
      + "  :retentionPeriod - inactivityPeriod IN (1, 30, 60) as remainingTime "
      + "FROM "
      + "  user_last_action "
      + "WHERE "
      + "  :retentionPeriod - inactivityPeriod IN (1, 30, 60)";

  private static final String RETENTION_PERIOD = "retentionPeriod";

  @Value("${rp.environment.variable.clean.expiredUser.retentionPeriod}")
  private long retentionPeriod;

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  private final RabbitTemplate rabbitTemplate;

  @Autowired
  public NotifyUserExpirationJob(JdbcTemplate jdbcTemplate,
      NamedParameterJdbcTemplate namedParameterJdbcTemplate,
      @Qualifier("rabbitTemplate") RabbitTemplate rabbitTemplate) {
    super(jdbcTemplate);
    this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    this.rabbitTemplate = rabbitTemplate;
  }

  @Override
  @Scheduled(cron = "${rp.environment.variable.notification.expiredUser.cron}")
  @SchedulerLock(name = "notifyUserExpiration")
  public void execute() {
    List<EmailNotificationRequest> notifications = getUsersForNotify();
    notifications.forEach(
        notification -> rabbitTemplate.convertAndSend(EXCHANGE_NOTIFICATION, QUEUE_EMAIL,
            notification));
  }

  private String getRemainingTime(long remainingTime) {
    if (remainingTime == 1) {
      return "tomorrow";
    } else if (remainingTime == 30) {
      return "1 month";
    } else if (remainingTime == 60) {
      return "2 months";
    } else {
      return remainingTime + DAYS;
    }
  }

  private List<EmailNotificationRequest> getUsersForNotify() {
    MapSqlParameterSource parameters = new MapSqlParameterSource();
    parameters.addValue(RETENTION_PERIOD, retentionPeriod);
    return namedParameterJdbcTemplate.query(
        SELECT_USERS_FOR_NOTIFY, parameters, (rs, rowNum) -> {
          Map<String, Object> params = new HashMap<>();
          params.put(INACTIVITY_PERIOD, rs.getLong(INACTIVITY_PERIOD) + DAYS);
          params.put(REMAINING_TIME, getRemainingTime(rs.getLong(REMAINING_TIME)));
          params.put(DEADLINE_DATE,
              String.valueOf(LocalDate.now().plusDays(rs.getLong(REMAINING_TIME))));
          EmailNotificationRequest emailNotificationRequest =
              new EmailNotificationRequest(rs.getString(EMAIL), USER_EXPIRATION_TEMPLATE);
          emailNotificationRequest.setParams(params);
          return emailNotificationRequest;
        });
  }
}
