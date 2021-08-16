package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.analyzer.index.IndexerServiceClient;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
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
public class CleanLogJob extends BaseCleanJob {

	private static final String PROJECT_ID_PARAM = "projectId";
	private static final String LOG_TIME_BOUND_PARAM = "timeBound";
	private static final String BATCH_SIZE_PARAM = "batchSize";
	private static final String IDS_PARAM = "ids";
	private static final String LOG_LEVEL_PARAM = "logLevel";

	private static final Integer ERROR_LOG_LEVEL = 40000;

	private static final String SELECT_LOG_IDS_QUERY = "SELECT id FROM log WHERE project_id = :projectId AND log_time <= :timeBound::TIMESTAMP LIMIT :batchSize";
	private static final String SELECT_LOG_IDS_BY_LEVEL_GTE_QUERY = "SELECT id FROM log WHERE id IN (:ids) AND log_level >= :logLevel";
	private static final String DELETE_LOGS_QUERY = "DELETE FROM log WHERE id IN (:ids)";

	private final Integer logBatchSize;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	private final CleanAttachmentJob cleanAttachmentJob;
	private final IndexerServiceClient indexerServiceClient;

	public CleanLogJob(JdbcTemplate jdbcTemplate, @Value("${rp.environment.variable.clean.log.batch}") Integer logBatchSize,
			NamedParameterJdbcTemplate namedParameterJdbcTemplate, CleanAttachmentJob cleanAttachmentJob,
			IndexerServiceClient indexerServiceClient) {
		super(jdbcTemplate);
		this.logBatchSize = logBatchSize;
		this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
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
		final AtomicInteger counter = new AtomicInteger(0);
		getProjectsWithAttribute(KEEP_LOGS).forEach((projectId, duration) -> {
			final Map<String, Object> selectParams = getSelectParams(projectId, duration);
			List<Long> ids = namedParameterJdbcTemplate.queryForList(SELECT_LOG_IDS_QUERY, selectParams, Long.class);
			while (!ids.isEmpty()) {
				int deleted = deleteLogs(projectId, ids);
				counter.addAndGet(deleted);
				ids = namedParameterJdbcTemplate.queryForList(SELECT_LOG_IDS_QUERY, selectParams, Long.class);
			}
		});
		logFinish(counter.get());
	}

	private Map<String, Object> getSelectParams(Long projectId, Duration duration) {
		final LocalDateTime lessThanDate = LocalDateTime.now(ZoneOffset.UTC).minus(duration);
		return Map.of(PROJECT_ID_PARAM, projectId, LOG_TIME_BOUND_PARAM, lessThanDate, BATCH_SIZE_PARAM, logBatchSize);
	}

	private int deleteLogs(Long projectId, List<Long> ids) {
		final List<Long> errorLogs = namedParameterJdbcTemplate.queryForList(SELECT_LOG_IDS_BY_LEVEL_GTE_QUERY,
				Map.of(IDS_PARAM, ids, LOG_LEVEL_PARAM, ERROR_LOG_LEVEL),
				Long.class
		);
		if (!errorLogs.isEmpty()) {
			indexerServiceClient.cleanIndex(projectId, errorLogs);
		}
		int deleted = namedParameterJdbcTemplate.update(DELETE_LOGS_QUERY, Map.of(IDS_PARAM, ids));
		LOGGER.info("Delete {} logs for project {}", deleted, projectId);
		return deleted;
	}
}
