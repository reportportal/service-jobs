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

import com.epam.reportportal.analyzer.index.IndexerServiceClient;
import com.epam.reportportal.jobs.BaseJob;
import com.epam.reportportal.model.EmailNotificationRequest;
import com.epam.reportportal.model.activity.event.ProjectDeletedEvent;
import com.epam.reportportal.model.activity.event.UnassignUserEvent;
import com.epam.reportportal.model.activity.event.UserDeletedEvent;
import com.epam.reportportal.service.MessageBus;
import com.epam.reportportal.storage.DataStorageService;
import com.epam.reportportal.utils.DataStorageUtils;
import com.epam.reportportal.utils.ValidationUtil;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * Deleting Users and their personal organizations by retention policy. Personal organizations and their projects are
 * cascade deleted when users are deleted.
 *
 * @author Andrei Piankouski
 */
@Service
@ConditionalOnProperty(prefix = "rp.environment.variable", name = "clean.expiredUser.retentionPeriod")
public class DeleteExpiredUsersJob extends BaseJob {

  public static final Logger LOGGER = LoggerFactory.getLogger(DeleteExpiredUsersJob.class);
  public static final String USER_IDS = "userIds";

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  private static final String RETENTION_PERIOD = "retentionPeriod";

  private static final String USER_DELETION_TEMPLATE = "userDeletionNotification";

  private static final String SELECT_EXPIRED_USERS = """
      SELECT
          u.id AS user_id,
          u.email AS user_email
      FROM users u
      LEFT JOIN api_keys ak ON u.id = ak.user_id
      WHERE
          (u.metadata->'metadata'->>'last_login')::BIGINT <= :retentionPeriod
          AND (ak.user_id IS NULL
              OR (EXTRACT(EPOCH FROM ak.last_used_at) * 1000)::BIGINT <= :retentionPeriod
              OR NOT EXISTS (SELECT 1 FROM api_keys WHERE user_id = u.id AND last_used_at IS NOT NULL))
          AND u.role != 'ADMINISTRATOR'
      GROUP BY u.id
      """;

  private static final String SELECT_PERSONAL_PROJECTS = """
      SELECT p.id AS project_id
      FROM project p
      JOIN organization o ON p.organization_id = o.id
      WHERE o.owner_id IN (:userIds)
      """;

  private static final String DELETE_ATTACHMENTS_BY_PROJECT = """
      WITH moved_rows AS (DELETE FROM attachment\s
      WHERE project_id = :projectId RETURNING id, file_id, thumbnail_id, creation_date)\s
      INSERT INTO attachment_deletion\s
      (id, file_id, thumbnail_id, creation_attachment_date, deletion_date)\s
      SELECT id, file_id, thumbnail_id, creation_date, NOW() FROM moved_rows""";

  private static final String DELETE_PROJECT_ISSUE_TYPES = """
      DELETE FROM issue_type\s
      WHERE id IN (
          SELECT it.id\s
          FROM issue_type it\s
          JOIN issue_type_project itp ON it.id = itp.issue_type_id\s
          WHERE itp.project_id = :projectId\s
          AND it.locator NOT IN ('pb001', 'ab001', 'si001', 'ti001', 'nd001'))""";

  private static final String DELETE_USERS = "DELETE FROM users WHERE id IN (:userIds)";

  private static final String SELECT_USERS_ATTACHMENTS = """
      SELECT attachment FROM users WHERE (id IN (:userIds) AND attachment IS NOT NULL)\s
      UNION\s
      SELECT attachment_thumbnail FROM users WHERE (id IN (:userIds) AND attachment_thumbnail IS NOT NULL)""";


  private static final String FIND_NON_PERSONAL_PROJECTS_BY_USER_IDS = """
      SELECT p.id, p.organization_id
      FROM project_user pu
      JOIN project p ON pu.project_id = p.id
      JOIN organization o ON p.organization_id = o.id
      WHERE o.owner_id IS NULL AND pu.user_id IN (:userIds)
      """;

  @Value("${rp.environment.variable.clean.expiredUser.retentionPeriod}")
  private Long retentionPeriod;
  private final DataStorageService dataStorageService;

  private final IndexerServiceClient indexerServiceClient;

  private final MessageBus messageBus;

  @Autowired
  public DeleteExpiredUsersJob(JdbcTemplate jdbcTemplate,
      NamedParameterJdbcTemplate namedParameterJdbcTemplate,
      DataStorageService dataStorageService, IndexerServiceClient indexerServiceClient,
      MessageBus messageBus) {
    super(jdbcTemplate);
    this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    this.dataStorageService = dataStorageService;
    this.indexerServiceClient = indexerServiceClient;
    this.messageBus = messageBus;
  }

  @Override
  @Scheduled(cron = "${rp.environment.variable.clean.expiredUser.cron}")
  @SchedulerLock(name = "deleteExpiredUsers", lockAtMostFor = "24h")
  public void execute() {
    if (ValidationUtil.isInvalidRetentionPeriod(retentionPeriod)) {
      LOGGER.info("No users are deleted");
      return;
    }

    List<User> users = findExpiredUsers();
    List<Long> userIds = getUserIds(users);
    List<Long> personalProjectIds = getPersonalProjectIds(userIds);
    List<ProjectOrganization> nonPersonalProjects = findNonPersonalProjectsByUserIds(userIds);

    deleteUsersPhoto(userIds);
    deleteProjectData(personalProjectIds);
    publishUnassignUserEvents(nonPersonalProjects);
    deleteUsersByIds(userIds);
    publishEmailNotificationEvents(getUserEmails(users));

    LOGGER.info("{} - users was deleted due to retention policy", userIds.size());
  }

  private void deleteProjectData(List<Long> personalProjectIds) {
    personalProjectIds.forEach(this::deleteProjectAssociatedData);
    messageBus.publishActivity(new ProjectDeletedEvent(personalProjectIds.size()));
  }

  private void deleteUsersPhoto(List<Long> userIds) {
    if (!CollectionUtils.isEmpty(userIds)) {
      MapSqlParameterSource parameters = new MapSqlParameterSource();
      parameters.addValue(USER_IDS, userIds);
      var userAttachments = namedParameterJdbcTemplate
          .queryForList(SELECT_USERS_ATTACHMENTS, parameters, String.class)
          .stream()
          .map(DataStorageUtils::decode)
          .toList();
      try {
        dataStorageService.deleteAll(userAttachments);
      } catch (Exception e) {
        LOGGER.error("Failed to delete users photo from data storage: {}", userAttachments, e);
      }
    }
  }

  private void publishEmailNotificationEvents(List<String> userEmails) {
    List<EmailNotificationRequest> notifications = userEmails.stream()
        .map(recipient -> new EmailNotificationRequest(recipient, USER_DELETION_TEMPLATE))
        .toList();
    messageBus.publishEmailNotificationEvents(notifications);
  }

  private void publishUnassignUserEvents(List<ProjectOrganization> nonPersonalProjects) {
    nonPersonalProjects.forEach(
        projectOrg -> messageBus.publishActivity(
            new UnassignUserEvent(projectOrg.getProjectId(), projectOrg.getOrganizationId())));
  }

  private List<ProjectOrganization> findNonPersonalProjectsByUserIds(List<Long> userIds) {
    if (CollectionUtils.isEmpty(userIds)) {
      return Collections.emptyList();
    }

    RowMapper<ProjectOrganization> rowMapper = (rs, rowNum) -> {
      ProjectOrganization projectOrg = new ProjectOrganization();
      projectOrg.setProjectId(rs.getLong("id"));
      projectOrg.setOrganizationId(rs.getLong("organization_id"));
      return projectOrg;
    };

    return namedParameterJdbcTemplate.query(FIND_NON_PERSONAL_PROJECTS_BY_USER_IDS,
        Map.of(USER_IDS, userIds), rowMapper);
  }

  private List<User> findExpiredUsers() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue(RETENTION_PERIOD, lastLoginBorder());

    RowMapper<User> rowMapper = (rs, rowNum) -> {
      User user = new User();
      user.setUserId(rs.getLong("user_id"));
      user.setEmail(rs.getString("user_email"));
      return user;
    };

    return namedParameterJdbcTemplate.query(SELECT_EXPIRED_USERS, params, rowMapper);
  }

  private List<Long> getPersonalProjectIds(List<Long> userIds) {
    if (CollectionUtils.isEmpty(userIds)) {
      return Collections.emptyList();
    }

    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue(USER_IDS, userIds);
    return namedParameterJdbcTemplate.queryForList(SELECT_PERSONAL_PROJECTS, params, Long.class);
  }

  private void deleteProjectAssociatedData(Long projectId) {
    deleteAttachmentsByProjectId(projectId);
    deleteProjectIssueTypes(projectId);
    indexerServiceClient.removeSuggest(projectId);
    indexerServiceClient.deleteIndex(projectId);
  }

  private void deleteUsersByIds(List<Long> userIds) {
    if (!userIds.isEmpty()) {
      MapSqlParameterSource params = new MapSqlParameterSource();
      params.addValue(USER_IDS, userIds);
      namedParameterJdbcTemplate.update(DELETE_USERS, params);
      messageBus.publishActivity(new UserDeletedEvent(userIds.size()));
    }
  }

  private void deleteProjectIssueTypes(Long projectId) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("projectId", projectId);
    namedParameterJdbcTemplate.update(DELETE_PROJECT_ISSUE_TYPES, params);
  }

  private void deleteAttachmentsByProjectId(Long projectId) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("projectId", projectId);
    namedParameterJdbcTemplate.update(DELETE_ATTACHMENTS_BY_PROJECT, params);
  }

  private long lastLoginBorder() {
    return LocalDateTime.now().minusDays(retentionPeriod).toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  private List<Long> getUserIds(List<User> users) {
    return users.stream()
        .map(User::getUserId)
        .collect(Collectors.toList());
  }

  private List<String> getUserEmails(List<User> users) {
    return users.stream()
        .map(User::getEmail)
        .collect(Collectors.toList());
  }


  static class User {

    private long userId;
    private String email;

    public long getUserId() {
      return userId;
    }

    public void setUserId(long userId) {
      this.userId = userId;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }
  }

  static class ProjectOrganization {

    private Long projectId;
    private Long organizationId;

    public Long getProjectId() {
      return projectId;
    }

    public void setProjectId(Long projectId) {
      this.projectId = projectId;
    }

    public Long getOrganizationId() {
      return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
      this.organizationId = organizationId;
    }
  }
}
