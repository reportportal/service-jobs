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
public class CleanAttachmentJob extends BaseJob {

	private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

	private static final String MOVING_QUERY = "WITH moved_rows AS (DELETE FROM attachment WHERE project_id = ? AND creation_date <= ?::TIMESTAMP RETURNING *) INSERT INTO attachment_tombstone SELECT * FROM moved_rows;";

	public CleanAttachmentJob(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	@Scheduled(cron = "${rp.environment.variable.clean.project.cron}")
	@SchedulerLock(name = "cleanProject", lockAtMostFor = "24h")
	public void execute() {
		moveAttachments();
	}

	private void moveAttachments() {
		LOGGER.info("Job {} has been started.", this.getClass().getSimpleName());

		Map<Long, String> projectsWithAttribute = getProjectsWithAttribute(ProjectAttributeEnum.KEEP_SCREENSHOTS);

		projectsWithAttribute.forEach((projectId, keepPeriod) -> {
			Duration ofSeconds = ofSeconds(NumberUtils.toLong(keepPeriod, 0L));
			if (!ofSeconds.isZero()) {
				LocalDateTime lessThanDate = LocalDateTime.now(ZoneOffset.UTC).minus(ofSeconds);
				int movedCount = jdbcTemplate.update(MOVING_QUERY, projectId, lessThanDate);
				LOGGER.info("Moved {} attachments to the tombstone table for project {}", movedCount, projectId);
			}
		});

		LOGGER.info("Job {} has been finished. Result {}", this.getClass().getSimpleName(), "FINISHED");
	}

}
