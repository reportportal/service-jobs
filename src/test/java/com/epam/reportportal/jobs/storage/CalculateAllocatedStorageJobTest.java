package com.epam.reportportal.jobs.storage;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
class CalculateAllocatedStorageJobTest {

  private static final ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
  private static final String SELECT_PROJECT_IDS_QUERY = "SELECT id FROM project ORDER BY id";
  private static final String SELECT_FILE_SIZE_SUM_BY_PROJECT_ID_QUERY =
      "SELECT coalesce(sum(file_size), 0) FROM attachment WHERE attachment.project_id = ?";
  private static final String UPDATE_ALLOCATED_STORAGE_BY_PROJECT_ID_QUERY =
      "UPDATE project SET allocated_storage = ? WHERE id = ?";

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

  private final CalculateAllocatedStorageJob calculateAllocatedStorageJob = new CalculateAllocatedStorageJob(
      taskExecutor,
      jdbcTemplate
  );

  @BeforeAll
  static void initExecutor() {
    taskExecutor.setCorePoolSize(2);
    taskExecutor.setMaxPoolSize(2);
    taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
    taskExecutor.afterPropertiesSet();
  }

  @AfterAll
  static void shutDownExecutor() {
    taskExecutor.shutdown();
  }

  @Test
  void shouldUpdateAllocatedStorageForAllProjects() {

    final List<Long> projectIds = List.of(1L, 2L);

    when(jdbcTemplate.queryForList(SELECT_PROJECT_IDS_QUERY, Long.class)).thenReturn(projectIds);
    when(jdbcTemplate.queryForObject(eq(SELECT_FILE_SIZE_SUM_BY_PROJECT_ID_QUERY), eq(Long.class),
        anyLong())).thenReturn(1000L);

    calculateAllocatedStorageJob.calculate();

    verify(jdbcTemplate, times(2)).queryForObject(eq(SELECT_FILE_SIZE_SUM_BY_PROJECT_ID_QUERY),
        eq(Long.class), anyLong());

    final ArgumentCaptor<Long> projectIdCaptor = ArgumentCaptor.forClass(Long.class);
    verify(jdbcTemplate, times(2)).update(eq(UPDATE_ALLOCATED_STORAGE_BY_PROJECT_ID_QUERY),
        anyLong(), projectIdCaptor.capture());
    final List<Long> updatedIds = projectIdCaptor.getAllValues();

    Assertions.assertEquals(projectIds.size(), updatedIds.size());
    Assertions.assertTrue(projectIds.containsAll(updatedIds));
  }

}