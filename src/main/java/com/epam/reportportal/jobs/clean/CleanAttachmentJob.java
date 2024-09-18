package com.epam.reportportal.jobs.clean;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Moving data from attachment table to attachment_deletion by storage policy for future deletion
 * that attachment from storage by another job.
 *
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanAttachmentJob extends BaseCleanJob {

  private static final String MOVING_QUERY = """
      WITH moved_rows AS (
        DELETE FROM attachment\s
        WHERE project_id = ?\s
        AND creation_date <= ?::TIMESTAMP\s
        AND launch_id NOT IN (
          SELECT id FROM launch WHERE retention_policy='IMPORTANT'
        )\s
        RETURNING *
      					)
           INSERT INTO attachment_deletion (id, file_id, thumbnail_id, creation_attachment_date,
      deletion_date)
      SELECT id, file_id, thumbnail_id, creation_date, NOW() FROM moved_rows;""";

  public CleanAttachmentJob(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  @Override
  @Scheduled(cron = "${rp.environment.variable.clean.attachment.cron}")
  @SchedulerLock(name = "cleanAttachment", lockAtMostFor = "24h")
  public void execute() {
    moveAttachments();
  }

  void moveAttachments() {
    LOGGER.info("CleanAttachmentJob has been started!!!");
    AtomicInteger counter = new AtomicInteger(0);
    getProjectsWithAttribute(KEEP_SCREENSHOTS).forEach((projectId, duration) -> {
      LocalDateTime lessThanDate = LocalDateTime.now(ZoneOffset.UTC).minus(duration);
      int movedCount = jdbcTemplate.update(MOVING_QUERY, projectId, lessThanDate);
      counter.addAndGet(movedCount);
      LOGGER.info(
          "Moved {} attachments to the deletion table for project {}, lessThanDate {} ", movedCount,
          projectId, lessThanDate
      );
    });
  }
}
