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
import com.epam.ta.reportportal.dao.AttachmentRepository;
import com.epam.ta.reportportal.dao.IssueTypeRepository;
import com.epam.ta.reportportal.dao.ProjectRepository;
import com.epam.ta.reportportal.dao.UserRepository;
import com.epam.ta.reportportal.entity.item.issue.IssueType;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jclouds.blobstore.BlobStore;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Deleting Users and their personal project by retention policy.
 *
 * @author Andrei Piankouski
 */
public class DeleteExpiredUsersJob extends BaseJob {

  public static final Logger LOGGER = LoggerFactory.getLogger(DeleteExpiredUsersJob.class);

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  private static final String RETENTION_PERIOD = "retentionPeriod";

  private static final String SELECT_EXPIRED_USERS = "SELECT u.id AS user_id, p.id AS project_id"
      + "FROM users u"
      + "LEFT JOIN api_keys ak ON u.id = ak.user_id"
      + "LEFT JOIN project p ON u.login || '_personal' = p.name AND p.project_type = 'PERSONAL'"
      + "WHERE (:now_ms - (u.metadata->'metadata'->>'last_login')::BIGINT) >= :retentionPeriod"
      + "AND ("
      + "ak.user_id IS NULL"
      + "OR (:now_ms - (EXTRACT(EPOCH FROM ak.last_used_at) * 1000)::BIGINT) >= :retentionPeriod"
      + "OR NOT EXISTS (SELECT 1 FROM api_keys WHERE user_id = u.id AND last_used_at IS NOT NULL)"
      + ")"
      + "AND u.role != 'ADMINISTRATOR'"
      + "GROUP BY u.id, p.id";

  private static final String SELECT_PROJECT_ISSUE_TYPES = "SELECT i.issue_id FROM issue i "
      + "JOIN issue_type_project itp ON i.issue_type = itp.issue_type_id "
      + "WHERE itp.project_id = :projectId";

  @Value("${rp.environment.variable.clean.expiredUser.retentionPeriod}")
  private long retentionPeriod;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ProjectRepository projectRepository;

  @Autowired
  private BlobStore blobStore;

  @Autowired
  private AttachmentRepository attachmentRepository;

  @Autowired
  private IssueTypeRepository issueTypeRepository;

  @Autowired
  private IndexerServiceClient indexerServiceClient;

  @Value("${datastore.bucketPrefix}")
  private String bucketPrefix;

  public DeleteExpiredUsersJob(JdbcTemplate jdbcTemplate,
      NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
    super(jdbcTemplate);
    this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
  }

  @Scheduled(cron = "${rp.environment.variable.clean.expiredUser.cron}")
  @SchedulerLock(name = "cleanStorage", lockAtMostFor = "24h")
  public void execute() {
    List<UserProject> userProjects = findUsersAndPersonalProjects();
    userRepository.deleteAllByIdInBatch(getUserIds(userProjects));
    Set<Long> defaultIssueTypes = getDefaultIssueTypes();
    getProjectIds(userProjects).forEach(id -> deleteProject(id, defaultIssueTypes));
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

  private void deleteProject(Long projectId, Set<Long> defaultIssueTypeIds) {
    Set<Long> issueTypesToRemove = getDefaultIssueTypes()
        .stream()
        .filter(issueTypeId -> !defaultIssueTypeIds.contains(issueTypeId))
        .collect(Collectors.toSet());
    projectRepository.deleteById(projectId);
    indexerServiceClient.removeSuggest(projectId);
    issueTypeRepository.deleteAllById(issueTypesToRemove);
    try {
      blobStore.deleteContainer(bucketPrefix + projectId);
    } catch (Exception e) {
      LOGGER.warn("Cannot delete attachments bucket " + bucketPrefix + projectId);
    }
    indexerServiceClient.deleteIndex(projectId);
    projectRepository.flush();
    attachmentRepository.moveForDeletionByProjectId(projectId);
    LOGGER.info("Project " + projectId + "was deleted.");
  }

  @NotNull
  private Set<Long> getDefaultIssueTypes() {
    return issueTypeRepository.getDefaultIssueTypes()
        .stream()
        .map(IssueType::getId)
        .collect(Collectors.toSet());
  }

  private List<Long> getProjectIssuesType(Long projectId) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("projectId", projectId);
    return namedParameterJdbcTemplate.queryForList(SELECT_PROJECT_ISSUE_TYPES, params, Long.class);
  }

  private long lastLoginBorder() {
    return LocalDateTime.now().minusDays(retentionPeriod).toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  public List<Long> getUserIds(List<UserProject> userProjects) {
    return userProjects.stream().map(UserProject::getUserId).collect(Collectors.toList());
  }

  public List<Long> getProjectIds(List<UserProject> userProjects) {
    return userProjects.stream().map(UserProject::getProjectId).collect(Collectors.toList());
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
