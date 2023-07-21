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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jclouds.blobstore.BlobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Deleting Users and their personal project by retention policy.
 *
 * @author Andrei Piankouski
 */
@Service
public class DeleteExpiredUsersJob extends BaseJob {

  public static final Logger LOGGER = LoggerFactory.getLogger(DeleteExpiredUsersJob.class);

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  private static final String RETENTION_PERIOD = "retentionPeriod";

  private static final String SELECT_EXPIRED_USERS = "SELECT u.id AS user_id, p.id AS project_id "
      + "FROM users u "
      + "LEFT JOIN api_keys ak ON u.id = ak.user_id "
      + "LEFT JOIN project p ON u.login || '_personal' = p.name AND p.project_type = 'PERSONAL' "
      + "WHERE (u.metadata->'metadata'->>'last_login')::BIGINT <= :retentionPeriod "
      + "AND ( "
      + "ak.user_id IS NULL "
      + "OR (EXTRACT(EPOCH FROM ak.last_used_at) * 1000)::BIGINT <= :retentionPeriod "
      + "OR NOT EXISTS (SELECT 1 FROM api_keys WHERE user_id = u.id AND last_used_at IS NOT NULL) "
      + ") "
      + "AND u.role != 'ADMINISTRATOR' "
      + "GROUP BY u.id, p.id";

  private static final String MOVE_ATTACHMENTS_TO_DELETE =
      "WITH moved_rows AS (DELETE FROM attachment "
          + "WHERE project_id = :projectId RETURNING id, file_id, thumbnail_id, creation_date) "
          + "INSERT INTO attachment_deletion "
          + "(id, file_id, thumbnail_id, creation_attachment_date, deletion_date) "
          + "SELECT id, file_id, thumbnail_id, creation_date, NOW() FROM moved_rows";

  private static final String DELETE_PROJECT_ISSUE_TYPES =
      "DELETE FROM issue_type "
          + "WHERE id IN ("
          + "    SELECT it.id "
          + "    FROM issue_type it "
          + "    JOIN issue_type_project itp ON it.id = itp.issue_type_id "
          + "    WHERE itp.project_id = :projectId "
          + "    AND it.locator NOT IN ('pb001', 'ab001', 'si001', 'ti001', 'nd001'))";

  private static final String DELETE_USERS = "DELETE FROM users WHERE id IN (:userIds)";

  private static final String DELETE_PROJECTS_BY_ID_LIST =
      "DELETE FROM project WHERE id IN (:projectIds)";

  @Value("${rp.environment.variable.clean.expiredUser.retentionPeriod}")
  private long retentionPeriod;

  private final BlobStore blobStore;

  private final IndexerServiceClient indexerServiceClient;

  @Value("${datastore.bucketPrefix}")
  private String bucketPrefix;

  @Autowired
  public DeleteExpiredUsersJob(JdbcTemplate jdbcTemplate,
      NamedParameterJdbcTemplate namedParameterJdbcTemplate,
      BlobStore blobStore, IndexerServiceClient indexerServiceClient) {
    super(jdbcTemplate);
    this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    this.blobStore = blobStore;
    this.indexerServiceClient = indexerServiceClient;
  }

  @Scheduled(cron = "${rp.environment.variable.clean.expiredUser.cron}")
  @SchedulerLock(name = "deleteExpiredUsers", lockAtMostFor = "24h")
  public void execute() {
    List<UserProject> userProjects = findUsersAndPersonalProjects();
    List<Long> userIds = getUserIds(userProjects);
    deleteUsersByIds(userIds);
    getProjectIds(userProjects).forEach(this::deleteProjectAssociatedData);
    deleteProjectsByIds(getProjectIds(userProjects));
    LOGGER.info("{} - users was deleted due to retention policy", userIds.size());
  }

  private List<UserProject> findUsersAndPersonalProjects() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("now_ms", System.currentTimeMillis());
    params.addValue(RETENTION_PERIOD, lastLoginBorder());

    RowMapper<UserProject> rowMapper = (rs, rowNum) -> {
      UserProject userProject = new UserProject();
      userProject.setUserId(rs.getLong("user_id"));
      userProject.setProjectId(rs.getLong("project_id"));
      return userProject;
    };

    return namedParameterJdbcTemplate.query(SELECT_EXPIRED_USERS, params, rowMapper);
  }

  private void deleteProjectAssociatedData(Long projectId) {
    deleteAttachmentsByProjectId(projectId);
    deleteProjectIssueTypes(projectId);
    indexerServiceClient.removeSuggest(projectId);
    try {
      blobStore.deleteContainer(bucketPrefix + projectId);
    } catch (Exception e) {
      LOGGER.warn("Cannot delete attachments bucket " + bucketPrefix + projectId);
    }
    indexerServiceClient.deleteIndex(projectId);
  }

  private void deleteUsersByIds(List<Long> userIds) {
    if (!userIds.isEmpty()) {
      MapSqlParameterSource params = new MapSqlParameterSource();
      params.addValue("userIds", userIds);
      namedParameterJdbcTemplate.update(DELETE_USERS, params);
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
    namedParameterJdbcTemplate.update(MOVE_ATTACHMENTS_TO_DELETE, params);
  }

  private void deleteProjectsByIds(List<Long> projectIds) {
    if (!projectIds.isEmpty()) {
      MapSqlParameterSource params = new MapSqlParameterSource();
      params.addValue("projectIds", projectIds);
      namedParameterJdbcTemplate.update(DELETE_PROJECTS_BY_ID_LIST, params);
    }
  }

  private long lastLoginBorder() {
    return LocalDateTime.now().minusDays(retentionPeriod).toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  public List<Long> getUserIds(List<UserProject> userProjects) {
    return userProjects.stream().map(UserProject::getUserId).collect(Collectors.toList());
  }

  public List<Long> getProjectIds(List<UserProject> userProjects) {
    return userProjects.stream().filter(Objects::nonNull).map(UserProject::getProjectId)
        .collect(Collectors.toList());
  }

  private static class UserProject {

    private long userId;
    private long projectId;

    public long getUserId() {
      return userId;
    }

    public void setUserId(long userId) {
      this.userId = userId;
    }

    public long getProjectId() {
      return projectId;
    }

    public void setProjectId(long projectId) {
      this.projectId = projectId;
    }
  }
}
