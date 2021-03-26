package com.epam.reportportal.jobs.clean;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanLogJob extends BaseCleanJob {

	private static final String DELETE_LOGS_QUERY = "DELETE FROM log WHERE project_id = ? AND log_time <= ?::TIMESTAMP;";

	private final CleanAttachmentJob cleanAttachmentJob;

	public CleanLogJob(JdbcTemplate jdbcTemplate, CleanAttachmentJob cleanAttachmentJob) {
		super(jdbcTemplate);
		this.cleanAttachmentJob = cleanAttachmentJob;
	}

	@Scheduled(cron = "${rp.environment.variable.clean.log.cron}")
	@SchedulerLock(name = "cleanLog", lockAtMostFor = "24h")
	public void execute() {
		removeLogs();
		cleanAttachmentJob.moveAttachments();
	}

	void removeLogs() {
		logStart();
		AtomicInteger counter = new AtomicInteger(0);
		getProjectsWithAttribute(KEEP_LOGS).forEach((projectId, duration) -> {
			LocalDateTime lessThanDate = LocalDateTime.now(ZoneOffset.UTC).minus(duration);
			int deleted = jdbcTemplate.update(DELETE_LOGS_QUERY, projectId, lessThanDate);
			if (deleted != 0) {
				counter.addAndGet(deleted);
				LOGGER.info("Delete {} logs for project {}", deleted, projectId);
			}
		});
		logFinish(counter.get());
	}
}
