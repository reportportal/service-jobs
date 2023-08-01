package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.analyzer.index.IndexerServiceClient;
import com.epam.reportportal.elastic.ElasticSearchClient;
import com.epam.reportportal.events.ElementsDeletedEvent;
import com.google.common.collect.Lists;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanLaunchJob extends BaseCleanJob {

	private static final String IDS_PARAM = "ids";
	private static final String PROJECT_ID_PARAM = "projectId";
	private static final String START_TIME_PARAM = "startTime";
	private final Integer batchSize;

	private static final String SELECT_LAUNCH_ID_QUERY = "SELECT id FROM launch WHERE project_id = :projectId AND start_time <= :startTime::TIMESTAMP;";
	private static final String DELETE_CLUSTER_QUERY = "DELETE FROM clusters WHERE clusters.launch_id IN (:ids);";
	private static final String DELETE_LAUNCH_QUERY = "DELETE FROM launch WHERE id IN (:ids);";

	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	private final CleanLogJob cleanLogJob;
	private final IndexerServiceClient indexerServiceClient;
	private final ApplicationEventPublisher eventPublisher;
	private final ElasticSearchClient elasticSearchClient;

	public CleanLaunchJob(@Value("${rp.environment.variable.elements-counter.batch-size}") Integer batchSize, JdbcTemplate jdbcTemplate,
			NamedParameterJdbcTemplate namedParameterJdbcTemplate, CleanLogJob cleanLogJob, IndexerServiceClient indexerServiceClient,
			ApplicationEventPublisher eventPublisher, ElasticSearchClient elasticSearchClient) {
		super(jdbcTemplate);
		this.batchSize = batchSize;
		this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
		this.cleanLogJob = cleanLogJob;
		this.indexerServiceClient = indexerServiceClient;
		this.eventPublisher = eventPublisher;
		this.elasticSearchClient = elasticSearchClient;
	}

	@Override
	@Scheduled(cron = "${rp.environment.variable.clean.launch.cron}")
	@SchedulerLock(name = "cleanLaunch", lockAtMostFor = "24h")
	public void execute() {
		removeLaunches();
		cleanLogJob.removeLogs();
	}

	private void removeLaunches() {
		AtomicInteger counter = new AtomicInteger(0);
		getProjectsWithAttribute(KEEP_LAUNCHES).forEach((projectId, duration) -> {
			final LocalDateTime lessThanDate = LocalDateTime.now(ZoneOffset.UTC).minus(duration);
			final List<Long> launchIds = getLaunchIds(projectId, lessThanDate);
			if (!launchIds.isEmpty()) {
				deleteClusters(launchIds);
//				final Long numberOfLaunchElements = countNumberOfLaunchElements(launchIds);
				int deleted = namedParameterJdbcTemplate.update(DELETE_LAUNCH_QUERY, Map.of(IDS_PARAM, launchIds));
				counter.addAndGet(deleted);
				LOGGER.info("Delete {} launches for project {}", deleted, projectId);
				// to avoid error message in analyzer log, doesn't find index
				if (deleted > 0) {
					indexerServiceClient.removeFromIndexLessThanLaunchDate(projectId, lessThanDate);
					LOGGER.info("Send message for deletion to analyzer for project {}", projectId);

					deleteLogsFromElasticsearchByLaunchIdsAndProjectId(launchIds, projectId);

//					eventPublisher.publishEvent(new ElementsDeletedEvent(launchIds, projectId, numberOfLaunchElements));
//					LOGGER.info("Send event with elements deleted number {} for project {}", deleted, projectId);
				}
			}
		});
	}

	private void deleteLogsFromElasticsearchByLaunchIdsAndProjectId(List<Long> launchIds, Long projectId) {
		for (Long launchId : launchIds) {
			elasticSearchClient.deleteLogsByLaunchIdAndProjectId(launchId, projectId);
			LOGGER.info("Delete logs from ES by launch {} and project {}", launchId, projectId);
		}
	}

	private List<Long> getLaunchIds(Long projectId, LocalDateTime lessThanDate) {
		return namedParameterJdbcTemplate.queryForList(SELECT_LAUNCH_ID_QUERY,
				Map.of(PROJECT_ID_PARAM, projectId, START_TIME_PARAM, lessThanDate),
				Long.class
		);
	}

	private void deleteClusters(List<Long> launchIds) {
		namedParameterJdbcTemplate.update(DELETE_CLUSTER_QUERY, Map.of(IDS_PARAM, launchIds));
	}

	private Long countNumberOfLaunchElements(List<Long> launchIds) {
		final AtomicLong resultedNumber = new AtomicLong(launchIds.size());
		final List<Long> itemIds = namedParameterJdbcTemplate.queryForList("SELECT item_id FROM test_item WHERE launch_id IN (:ids) UNION "
						+ "SELECT item_id FROM test_item WHERE retry_of IS NOT NULL AND retry_of IN "
						+ "(SELECT item_id FROM test_item WHERE launch_id IN (:ids))",
				Map.of(IDS_PARAM, launchIds),
				Long.class
		);
		resultedNumber.addAndGet(itemIds.size());
		Lists.partition(itemIds, batchSize)
				.forEach(batch -> resultedNumber.addAndGet(Optional.ofNullable(namedParameterJdbcTemplate.queryForObject(
						"SELECT COUNT(*) FROM log WHERE item_id IN (:ids);",
						Map.of(IDS_PARAM, batch),
						Long.class
				)).orElse(0L)));
		resultedNumber.addAndGet(Optional.ofNullable(namedParameterJdbcTemplate.queryForObject("SELECT COUNT(*) FROM log WHERE log.launch_id IN (:ids);",
				Map.of(IDS_PARAM, launchIds),
				Long.class
		)).orElse(0L));
		return resultedNumber.longValue();
	}
}
