package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.analyzer.index.IndexerServiceClient;
import com.epam.reportportal.elastic.SimpleElasticSearchClient;
import com.epam.reportportal.events.ElementsDeletedEvent;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
	private static final String START_TIME_PARAM = "startTime";

	private static final String DELETE_LOGS_QUERY = "DELETE FROM log WHERE project_id = ? AND log_time <= ?::TIMESTAMP;";
	private static final String SELECT_LAUNCH_ID_QUERY = "SELECT id FROM launch WHERE project_id = :projectId AND start_time <= :startTime::TIMESTAMP;";

	private final CleanAttachmentJob cleanAttachmentJob;
	private final IndexerServiceClient indexerServiceClient;
	private final ApplicationEventPublisher eventPublisher;
	private final SimpleElasticSearchClient elasticSearchClient;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	public CleanLogJob(JdbcTemplate jdbcTemplate, CleanAttachmentJob cleanAttachmentJob,
					   IndexerServiceClient indexerServiceClient, ApplicationEventPublisher eventPublisher,
					   SimpleElasticSearchClient elasticSearchClient, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
		super(jdbcTemplate);
		this.cleanAttachmentJob = cleanAttachmentJob;
		this.indexerServiceClient = indexerServiceClient;
		this.eventPublisher = eventPublisher;
		this.elasticSearchClient = elasticSearchClient;
		this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
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
		// TODO: Need to refactor Logs to keep real it's launchId and combine code with
		// CleanLaunch to avoid duplication
		getProjectsWithAttribute(KEEP_LOGS).forEach((projectId, duration) -> {
			final LocalDateTime lessThanDate = LocalDateTime.now(ZoneOffset.UTC).minus(duration);
			int deleted = jdbcTemplate.update(DELETE_LOGS_QUERY, projectId, lessThanDate);
			counter.addAndGet(deleted);
			LOGGER.info("Delete {} logs for project {}", deleted, projectId);
			// to avoid error message in analyzer log, doesn't find index
			if (deleted > 0) {
				indexerServiceClient.removeFromIndexLessThanLogDate(projectId, lessThanDate);
				LOGGER.info("Send message for deletion to analyzer for project {}", projectId);

				final List<Long> launchIds = getLaunchIds(projectId, lessThanDate);
				if (!launchIds.isEmpty()) {
					deleteLogsFromElasticsearchByLaunchIdsAndProjectId(launchIds, projectId);
				}

				eventPublisher.publishEvent(new ElementsDeletedEvent(this, projectId, deleted));
				LOGGER.info("Send event with elements deleted number {} for project {}", deleted, projectId);
			}
		});

		logFinish(counter.get());
	}

	private void deleteLogsFromElasticsearchByLaunchIdsAndProjectId(List<Long> launchIds, Long projectId) {
		for (Long launchId : launchIds) {
			elasticSearchClient.deleteStreamByLaunchIdAndProjectId(launchId, projectId);
			LOGGER.info("Delete logs from ES by launch {} and project {}", launchId, projectId);
		}
	}

	private List<Long> getLaunchIds(Long projectId, LocalDateTime lessThanDate) {
		return namedParameterJdbcTemplate.queryForList(SELECT_LAUNCH_ID_QUERY,
				Map.of(PROJECT_ID_PARAM, projectId, START_TIME_PARAM, lessThanDate),
				Long.class
		);
	}
}
