package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.analyzer.index.IndexerServiceClient;
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
	private final IndexerServiceClient indexerServiceClient;

	public CleanLogJob(JdbcTemplate jdbcTemplate, CleanAttachmentJob cleanAttachmentJob,
					   IndexerServiceClient indexerServiceClient) {
		super(jdbcTemplate);
		this.cleanAttachmentJob = cleanAttachmentJob;
		this.indexerServiceClient = indexerServiceClient;
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
			final LocalDateTime lessThanDate = LocalDateTime.now(ZoneOffset.UTC).minus(duration);
			int deleted = jdbcTemplate.update(DELETE_LOGS_QUERY, projectId, lessThanDate);
			counter.addAndGet(deleted);
			LOGGER.info("Delete {} logs for project {}", deleted, projectId);
			// to avoid error message in analyzer log, doesn't find index
			if (deleted > 0) {
				indexerServiceClient.removeFromIndexLessThanLogDate(projectId, lessThanDate);
				LOGGER.info("Send message for deletion to analyzer for project {}", projectId);
			}
		});
		logFinish(counter.get());
	}
}
