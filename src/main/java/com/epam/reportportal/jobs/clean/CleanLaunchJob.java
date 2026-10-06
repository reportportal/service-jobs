package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.analyzer.index.IndexerServiceClient;
import com.epam.reportportal.elastic.SearchEngineClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanLaunchJob extends BaseCleanJob {

  private static final String IDS_PARAM = "ids";
  private static final String PROJECT_ID_PARAM = "projectId";
  private static final String START_TIME_PARAM = "startTime";
  private static final String SELECT_LAUNCH_ID_QUERY =
      "SELECT id FROM launch WHERE project_id = :projectId AND start_time <= "
          + ":startTime::TIMESTAMP AND retention_policy = 'REGULAR' AND launch_type <> 'MANUAL'";
  private static final String DELETE_CLUSTER_QUERY =
      "DELETE FROM clusters WHERE clusters.launch_id IN (:ids);";
  private static final String DELETE_LAUNCH_QUERY = "DELETE FROM launch WHERE id IN (:ids);";
  private final Integer batchSize;
  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
  private final CleanLogJob cleanLogJob;
  private final IndexerServiceClient indexerServiceClient;
  private final SearchEngineClient searchEngineClient;

  public CleanLaunchJob(
      @Value("${rp.environment.variable.batch-size:10000}") Integer batchSize,
      JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
      CleanLogJob cleanLogJob, IndexerServiceClient indexerServiceClient,
      SearchEngineClient searchEngineClient) {
    super(jdbcTemplate);
    this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    this.cleanLogJob = cleanLogJob;
    this.indexerServiceClient = indexerServiceClient;
    this.searchEngineClient = searchEngineClient;
    this.batchSize = batchSize > 65535 ? 65535 : batchSize;
  }

  @Override
  @Scheduled(cron = "${rp.environment.variable.clean.launch.cron}")
  @SchedulerLock(name = "cleanLaunch", lockAtMostFor = "24h")
  public void execute() {
    getProjectsWithAttribute(KEEP_LAUNCHES)
        .forEach(this::removeLaunches);
    cleanLogJob.removeLogs();
  }

  private void removeLaunches(Long projectId, Duration duration) {
    try {
      final LocalDateTime lessThanDate = LocalDateTime.now(ZoneOffset.UTC).minus(duration);
      final List<Long> allLaunchIds = getLaunchIds(projectId, lessThanDate);
      IntStream.iterate(0, i -> i < allLaunchIds.size(), i -> i + batchSize)
          .mapToObj(i -> allLaunchIds.subList(i, Math.min(i + batchSize, allLaunchIds.size())))
          .forEach(launchIds -> {
            deleteClusters(launchIds);
            int deleted = namedParameterJdbcTemplate.update(DELETE_LAUNCH_QUERY,
                Map.of(IDS_PARAM, launchIds));
            LOGGER.info("Delete {} launches for project {}", deleted, projectId);
            // to avoid an error message in the analyzer log, doesn't find the index
            if (deleted > 0) {
              indexerServiceClient.removeFromIndexLessThanLaunchDate(projectId, lessThanDate);
              LOGGER.info("Send message for deletion to analyzer for project {}", projectId);

              deleteLogsFromSearchEngineByLaunchIdsAndProjectId(launchIds, projectId);
            }
          });
    } catch (Exception e) {
      LOGGER.error("Error occurred while removing launches for project {}", projectId, e);
    }
  }


  private void deleteLogsFromSearchEngineByLaunchIdsAndProjectId(List<Long> launchIds,
      Long projectId) {
    for (Long launchId : launchIds) {
      searchEngineClient.deleteLogsByLaunchIdAndProjectId(launchId, projectId);
      LOGGER.info("Delete logs from ES by launch {} and project {}", launchId, projectId);
    }
  }

  private List<Long> getLaunchIds(Long projectId, LocalDateTime lessThanDate) {
    return namedParameterJdbcTemplate.queryForList(SELECT_LAUNCH_ID_QUERY,
        Map.of(PROJECT_ID_PARAM, projectId, START_TIME_PARAM, lessThanDate), Long.class
    );
  }

  private void deleteClusters(List<Long> launchIds) {
    namedParameterJdbcTemplate.update(DELETE_CLUSTER_QUERY, Map.of(IDS_PARAM, launchIds));
  }
}
