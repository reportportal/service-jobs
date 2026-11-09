package com.epam.reportportal.jobs.clean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.epam.reportportal.analyzer.index.IndexerServiceClient;
import com.epam.reportportal.elastic.SearchEngineClient;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class CleanLaunchJobTest {

  private static final long PROJECT_ID = 1L;
  private static final String DELETE_LAUNCH_QUERY = "DELETE FROM launch WHERE id IN (:ids);";

  @Mock
  private JdbcTemplate jdbcTemplate;
  @Mock
  private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
  @Mock
  private CleanLogJob cleanLogJob;
  @Mock
  private IndexerServiceClient indexerServiceClient;
  @Mock
  private SearchEngineClient searchEngineClient;

  private CleanLaunchJob job;

  @BeforeEach
  void setUp() {
    job = spy(new CleanLaunchJob(10000, jdbcTemplate, namedParameterJdbcTemplate, cleanLogJob,
        indexerServiceClient, searchEngineClient));
    lenient().doReturn(Map.of(PROJECT_ID, Duration.ofDays(30)))
        .when(job).getProjectsWithAttribute(BaseCleanJob.KEEP_LAUNCHES);
  }

  @Test
  void selectQuery_includesManualLaunchesAndKeepsImportantOnes() throws Exception {
    String query = selectQuery();

    assertFalse(query.contains("launch_type"),
        "Manual launches must follow the same retention policy as automated launches");
    assertTrue(query.contains("retention_policy = 'REGULAR'"),
        "Important launches must still be kept");
  }

  @Test
  void execute_withExpiredLaunches_deletesThemAndNotifiesAnalyzerAndSearchEngine()
      throws Exception {
    // 10 = automated, 20 = manual: the job does not distinguish them
    when(namedParameterJdbcTemplate.queryForList(eq(selectQuery()), anyMap(), eq(Long.class)))
        .thenReturn(List.of(10L, 20L));
    when(namedParameterJdbcTemplate.update(anyString(), anyMap())).thenReturn(2);

    job.execute();

    verify(namedParameterJdbcTemplate).update(DELETE_LAUNCH_QUERY, Map.of("ids", List.of(10L, 20L)));
    verify(indexerServiceClient).removeFromIndexLessThanLaunchDate(eq(PROJECT_ID),
        any(LocalDateTime.class));
    verify(searchEngineClient).deleteLogsByLaunchIdAndProjectId(10L, PROJECT_ID);
    verify(searchEngineClient).deleteLogsByLaunchIdAndProjectId(20L, PROJECT_ID);
    verify(cleanLogJob).removeLogs();
  }

  @Test
  void execute_withNoExpiredLaunches_deletesNothing() throws Exception {
    when(namedParameterJdbcTemplate.queryForList(eq(selectQuery()), anyMap(), eq(Long.class)))
        .thenReturn(List.of());

    job.execute();

    verify(namedParameterJdbcTemplate, never()).update(anyString(), anyMap());
    verify(indexerServiceClient, never()).removeFromIndexLessThanLaunchDate(any(), any());
    verify(cleanLogJob).removeLogs();
  }

  @Test
  void execute_withKeepLaunchesForever_doesNotSelectLaunches() {
    // Forever is stored as '0' and filtered out by getProjectsWithAttribute
    lenient().doReturn(Map.of()).when(job).getProjectsWithAttribute(BaseCleanJob.KEEP_LAUNCHES);

    job.execute();

    verify(namedParameterJdbcTemplate, never()).queryForList(anyString(), anyMap(), eq(Long.class));
    verify(namedParameterJdbcTemplate, never()).update(anyString(), anyMap());
  }

  private static String selectQuery() throws Exception {
    Field field = CleanLaunchJob.class.getDeclaredField("SELECT_LAUNCH_ID_QUERY");
    field.setAccessible(true);
    return (String) field.get(null);
  }
}
