package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.analyzer.index.IndexerServiceClient;
import com.epam.reportportal.elastic.SearchEngineClient;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanLogJob extends BaseCleanJob {

  private static final String PROJECT_ID_PARAM = "projectId";
  private static final String START_TIME_PARAM = "startTime";

  private static final String DELETE_LOGS_QUERY = """
      DELETE FROM log
      WHERE log.project_id = ? AND log.log_time <= ?::TIMESTAMP
        AND COALESCE(log.launch_id,
                     (SELECT test_item.launch_id FROM test_item WHERE test_item.item_id = log.item_id),
                     (SELECT test_item.launch_id FROM test_item WHERE test_item.item_id =
                                                                      (SELECT ti.retry_of FROM test_item ti WHERE ti.item_id = log.item_id)
                     )
                ) IN (SELECT launch.id FROM launch WHERE launch.retention_policy = 'REGULAR');
      """;
  private static final String SELECT_LAUNCH_ID_QUERY =
      "SELECT id FROM launch WHERE project_id = :projectId AND start_time <= "
          + ":startTime::TIMESTAMP;";

  private final CleanAttachmentJob cleanAttachmentJob;
  private final IndexerServiceClient indexerServiceClient;
  private final ApplicationEventPublisher eventPublisher;
  private final SearchEngineClient searchEngineClient;
  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  public CleanLogJob(JdbcTemplate jdbcTemplate, CleanAttachmentJob cleanAttachmentJob,
      IndexerServiceClient indexerServiceClient, ApplicationEventPublisher eventPublisher,
      SearchEngineClient searchEngineClient,
      NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
    super(jdbcTemplate);
    this.cleanAttachmentJob = cleanAttachmentJob;
    this.indexerServiceClient = indexerServiceClient;
    this.eventPublisher = eventPublisher;
    this.searchEngineClient = searchEngineClient;
    this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
  }

  @Override
  @Scheduled(cron = "${rp.environment.variable.clean.log.cron}")
  @SchedulerLock(name = "cleanLog", lockAtMostFor = "24h")
  public void execute() {
    removeLogs();
  }

  void removeLogs() {
    LOGGER.info("CleanLogJob has been started!!!");
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
          deleteLogsFromSearchEngineByLaunchIdsAndProjectId(launchIds, projectId);
        }
      }
    });
    cleanAttachmentJob.moveAttachments();
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
}
