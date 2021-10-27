package com.epam.reportportal.jobs.clean;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanLaunchJob extends BaseCleanJob {

	private static final String IDS_PARAM = "ids";
	private static final String PROJECT_ID_PARAM = "projectId";
	private static final String START_TIME_PARAM = "startTime";

	private static final String SELECT_LAUNCH_ID_QUERY = "SELECT id FROM launch WHERE project_id = :projectId AND start_time <= :startTime::TIMESTAMP;";
	private static final String DELETE_CLUSTER_QUERY = "DELETE FROM clusters WHERE clusters.launch_id IN (:ids);";
	private static final String DELETE_LAUNCH_QUERY = "DELETE FROM launch WHERE id IN (:ids);";

	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	private final CleanLogJob cleanLogJob;

	public CleanLaunchJob(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate, CleanLogJob cleanLogJob) {
		super(jdbcTemplate);
		this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
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
		getProjectsWithAttribute(KEEP_LAUNCHES).forEach((projectId, duration) -> {
			final List<Long> launchIds = getLaunchIds(projectId, duration);
			if (!launchIds.isEmpty()) {
				deleteClusters(launchIds);
				int deleted = namedParameterJdbcTemplate.update(DELETE_LAUNCH_QUERY, Map.of(IDS_PARAM, launchIds));
				counter.addAndGet(deleted);
				LOGGER.info("Delete {} launches for project {}", deleted, projectId);
			}
		});
		logFinish(counter.get());
	}

	private List<Long> getLaunchIds(Long projectId, Duration duration) {
		final LocalDateTime lessThanDate = LocalDateTime.now(ZoneOffset.UTC).minus(duration);
		return namedParameterJdbcTemplate.queryForList(SELECT_LAUNCH_ID_QUERY,
				Map.of(PROJECT_ID_PARAM, projectId, START_TIME_PARAM, lessThanDate),
				Long.class
		);
	}

	private void deleteClusters(List<Long> launchIds) {
		namedParameterJdbcTemplate.update(DELETE_CLUSTER_QUERY, Map.of(IDS_PARAM, launchIds));
	}
}
