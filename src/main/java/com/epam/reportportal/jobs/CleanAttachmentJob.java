package com.epam.reportportal.jobs;

import com.epam.reportportal.old_jobs.entity.EntityUtils;
import com.epam.reportportal.old_jobs.entity.Project;
import com.epam.reportportal.old_jobs.entity.ProjectAttributeEnum;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static java.time.Duration.ofSeconds;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanAttachmentJob extends BaseJob {

	private static final String MOVING_QUERY = "WITH moved_rows AS " +
			"(DELETE FROM attachment WHERE project_id = :id AND creation_date <= :date::TIMESTAMP RETURNING *) " +
			"INSERT INTO attachment_tombstone SELECT * FROM moved_rows;";

	public CleanAttachmentJob(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	/**
	 * Moves attachments to the tombstone table for further deleting by a different job
	*/
	@Scheduled(cron = "${rp.environment.variable.clean.project.cron}")
	@SchedulerLock(name = "cleanProject", lockAtMostFor = "24h")
	public void execute() {
		LOGGER.info("Job {} has been started.", this.getClass().getSimpleName());
		Object result = null;

		Project project = null;

		EntityUtils.extractAttributeValue(project, ProjectAttributeEnum.KEEP_SCREENSHOTS.getAttribute())
				.map(keepPeriod -> ofSeconds(NumberUtils.toLong(keepPeriod, 0L)))
				.map(keepPeriod -> LocalDateTime.now(ZoneOffset.UTC).minus(keepPeriod))
				.ifPresent(lessThanDate -> jdbcTemplate.update(MOVING_QUERY, project.getId(), lessThanDate));

		LOGGER.info("Job {} has been finished. Result {}", this.getClass().getSimpleName(), result);
	}
}
