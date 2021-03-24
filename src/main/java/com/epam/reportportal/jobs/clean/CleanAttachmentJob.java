package com.epam.reportportal.jobs.clean;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static java.time.Duration.ofSeconds;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanAttachmentJob extends BaseCleanJob {

	private static final String MOVING_QUERY = "WITH moved_rows AS (DELETE FROM attachment WHERE project_id = ? " +
			"AND creation_date <= ?::TIMESTAMP RETURNING *) " +
			"INSERT INTO attachment_deletion (id, file_id, thumbnail_id, creation_attachment_date, deletion_date)  " +
			"SELECT id, file_id, thumbnail_id, creation_date, now() FROM moved_rows;";

	public CleanAttachmentJob(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Scheduled(cron = "${rp.environment.variable.clean.attachment.cron}")
	@SchedulerLock(name = "cleanAttachment", lockAtMostFor = "24h")
	public void execute() {
		logStart();
		moveAttachments();
		logFinish();
	}

	private void moveAttachments() {
		Map<Long, String> projectsWithAttribute = getProjectsWithAttribute(KEEP_SCREENSHOTS);

		projectsWithAttribute.forEach((projectId, keepPeriod) -> {
			Duration ofSeconds = ofSeconds(NumberUtils.toLong(keepPeriod, 0L));
			if (!ofSeconds.isZero()) {
				LocalDateTime lessThanDate = LocalDateTime.now(ZoneOffset.UTC).minus(ofSeconds);
				int movedCount = jdbcTemplate.update(MOVING_QUERY, projectId, lessThanDate);
				LOGGER.info("Moved {} attachments to the tombstone table for project {}", movedCount, projectId);
			}
		});
	}
}
