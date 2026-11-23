package com.epam.reportportal.jobs.clean;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes TMS data of manual launches that were already deleted (e.g. by the retention job) and moves the comment
 * attachments of that data to attachment_deletion, so the files are removed from the storage by
 * {@link CleanStorageJob}, the same way as for automated launches.
 */
@Service
public class CleanTmsAttachmentJob extends BaseCleanJob {

  private static final String IDS_PARAM = "ids";
  private static final int MAX_PARAMS = 65535;

  private static final String SELECT_ORPHAN_EXECUTIONS =
      """
      SELECT e.id FROM tms_test_case_execution e
      WHERE NOT EXISTS (SELECT 1 FROM launch l WHERE l.id = e.launch_id)
      LIMIT ?
      """;

  private static final String SELECT_EXECUTION_ATTACHMENTS =
      """
      SELECT DISTINCT ca.attachment_id
      FROM tms_test_case_execution_comment_attachment ca
      JOIN tms_test_case_execution_comment c ON c.id = ca.execution_comment_id
      WHERE c.execution_id IN (:ids)
      """;

  private static final String DELETE_COMMENT_ATTACHMENTS =
      """
      DELETE FROM tms_test_case_execution_comment_attachment
      WHERE execution_comment_id IN
        (SELECT id FROM tms_test_case_execution_comment WHERE execution_id IN (:ids))
      """;

  private static final String DELETE_COMMENTS =
      "DELETE FROM tms_test_case_execution_comment WHERE execution_id IN (:ids)";

  private static final String DELETE_STEP_EXECUTIONS =
      "DELETE FROM tms_step_execution WHERE test_case_execution_id IN (:ids)";

  private static final String DELETE_EXECUTIONS =
      "DELETE FROM tms_test_case_execution WHERE id IN (:ids)";

  private static final String DELETE_ORPHAN_FOLDER_ITEMS =
      """
      DELETE FROM tms_test_folder_test_item fti
      WHERE NOT EXISTS (SELECT 1 FROM launch l WHERE l.id = fti.launch_id)
      """;

  /**
   * Moves the given attachments to attachment_deletion unless they are still used somewhere. A new id from
   * attachment_id_seq is used, so it cannot clash with ids of automated attachments.
   */
  private static final String MOVE_ATTACHMENTS =
      """
      WITH moved_rows AS (
        DELETE FROM tms_attachment a
        WHERE a.id IN (:ids)
          AND NOT EXISTS (SELECT 1 FROM tms_test_case_execution_comment_attachment x
                          WHERE x.attachment_id = a.id)
          AND NOT EXISTS (SELECT 1 FROM tms_step_attachment x WHERE x.attachment_id = a.id)
          AND NOT EXISTS (SELECT 1 FROM tms_text_manual_scenario_attachment x
                          WHERE x.attachment_id = a.id)
          AND NOT EXISTS (SELECT 1 FROM tms_manual_scenario_preconditions_attachment x
                          WHERE x.attachment_id = a.id)
        RETURNING a.path_to_file, a.thumbnail_path, a.created_at
      )
      INSERT INTO attachment_deletion (id, file_id, thumbnail_id, creation_attachment_date, deletion_date)
      SELECT nextval('attachment_id_seq'), path_to_file, thumbnail_path, created_at,
        NOW() AT TIME ZONE 'UTC'
      FROM moved_rows
      """;

  /**
   * Moves comment attachments of test executions older than the date, except ones of IMPORTANT
   * launches or still used somewhere else.
   */
  private static final String MOVE_EXPIRED_ATTACHMENTS =
      """
      WITH moved_rows AS (
        DELETE FROM tms_attachment a
        WHERE a.project_id = ?
          AND a.created_at <= ?::TIMESTAMP
          AND EXISTS (SELECT 1 FROM tms_test_case_execution_comment_attachment x
                      WHERE x.attachment_id = a.id)
          AND NOT EXISTS (SELECT 1 FROM tms_test_case_execution_comment_attachment x
                          JOIN tms_test_case_execution_comment c ON c.id = x.execution_comment_id
                          JOIN tms_test_case_execution e ON e.id = c.execution_id
                          JOIN launch l ON l.id = e.launch_id
                          WHERE x.attachment_id = a.id AND l.retention_policy = 'IMPORTANT')
          AND NOT EXISTS (SELECT 1 FROM tms_step_attachment x WHERE x.attachment_id = a.id)
          AND NOT EXISTS (SELECT 1 FROM tms_text_manual_scenario_attachment x
                          WHERE x.attachment_id = a.id)
          AND NOT EXISTS (SELECT 1 FROM tms_manual_scenario_preconditions_attachment x
                          WHERE x.attachment_id = a.id)
        RETURNING a.path_to_file, a.thumbnail_path, a.created_at
      )
      INSERT INTO attachment_deletion (id, file_id, thumbnail_id, creation_attachment_date,
        deletion_date)
      SELECT nextval('attachment_id_seq'), path_to_file, thumbnail_path, created_at,
        NOW() AT TIME ZONE 'UTC'
      FROM moved_rows
      """;

  private final int batchSize;
  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  public CleanTmsAttachmentJob(JdbcTemplate jdbcTemplate,
      NamedParameterJdbcTemplate namedParameterJdbcTemplate,
      @Value("${rp.environment.variable.batch-size:10000}") int batchSize) {
    super(jdbcTemplate);
    this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    this.batchSize = Math.min(batchSize, 65535);
  }

  @Override
  @Scheduled(cron = "${rp.environment.variable.clean.attachment.cron}")
  @SchedulerLock(name = "cleanTmsAttachment", lockAtMostFor = "24h")
  @Transactional
  public void execute() {
    moveExpiredAttachments();
    removeOrphanExecutions();
  }

  void moveExpiredAttachments() {
    getProjectsWithAttribute(KEEP_SCREENSHOTS).forEach((projectId, duration) -> {
      LocalDateTime lessThanDate = LocalDateTime.now(ZoneOffset.UTC).minus(duration);
      int moved = jdbcTemplate.update(MOVE_EXPIRED_ATTACHMENTS, projectId, lessThanDate);
      LOGGER.info("Moved {} TMS attachments to the deletion table for project {}, lessThanDate {}",
          moved, projectId, lessThanDate);
    });
  }

  void removeOrphanExecutions() {
    int movedTotal = 0;
    List<Long> executionIds;
    while (!(executionIds = jdbcTemplate.queryForList(SELECT_ORPHAN_EXECUTIONS, Long.class, batchSize)).isEmpty()) {
      movedTotal += removeExecutions(executionIds);
    }
    int folderItems = jdbcTemplate.update(DELETE_ORPHAN_FOLDER_ITEMS);
    LOGGER.info("Moved {} TMS attachments to the deletion table, removed {} orphan folder items",
        movedTotal, folderItems);
  }

  private int removeExecutions(List<Long> executionIds) {
    Map<String, Object> params = Map.of(IDS_PARAM, executionIds);
    List<Long> attachmentIds = namedParameterJdbcTemplate.queryForList(
        SELECT_EXECUTION_ATTACHMENTS, params, Long.class);

    namedParameterJdbcTemplate.update(DELETE_COMMENT_ATTACHMENTS, params);
    namedParameterJdbcTemplate.update(DELETE_COMMENTS, params);
    namedParameterJdbcTemplate.update(DELETE_STEP_EXECUTIONS, params);
    namedParameterJdbcTemplate.update(DELETE_EXECUTIONS, params);

    if (attachmentIds.isEmpty()) {
      return 0;
    }
    int moved = 0;
    for (int i = 0; i < attachmentIds.size(); i += MAX_PARAMS) {
      List<Long> chunk = attachmentIds.subList(i, Math.min(i + MAX_PARAMS, attachmentIds.size()));
      moved += namedParameterJdbcTemplate.update(MOVE_ATTACHMENTS, Map.of(IDS_PARAM, chunk));
    }
    return moved;
  }
}
