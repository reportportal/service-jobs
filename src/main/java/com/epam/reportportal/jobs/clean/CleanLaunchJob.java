package com.epam.reportportal.jobs.clean;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.Duration.ofSeconds;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanLaunchJob extends BaseCleanJob {

	private static final String DELETE_LAUNCH_QUERY = "DELETE FROM launch WHERE project_id = ? AND start_time <= ?::TIMESTAMP;";

	private final CleanLogJob cleanLogJob;

	public CleanLaunchJob(JdbcTemplate jdbcTemplate, CleanLogJob cleanLogJob) {
		super(jdbcTemplate);
		this.cleanLogJob = cleanLogJob;
	}

	@Scheduled(cron = "${rp.environment.variable.clean.launch.cron}")
	@SchedulerLock(name = "cleanLaunch", lockAtMostFor = "24h")
	public void execute() {
		removeLaunches();
		cleanLogJob.removeLogs();
	}

	private void removeLaunches() {
		logStart();
		AtomicInteger counter = new AtomicInteger(0);
		getProjectsWithAttribute(KEEP_LAUNCHES).forEach((projectId, keepPeriod) -> {
			LocalDateTime lessThanDate = LocalDateTime.now(ZoneOffset.UTC).minus(ofSeconds(keepPeriod));
			int deleted = jdbcTemplate.update(DELETE_LAUNCH_QUERY, projectId, lessThanDate);
			counter.addAndGet(deleted);
			LOGGER.info("Delete {} launches for project {}", deleted, projectId);
		});
		logFinish(counter.get());
	}
}
