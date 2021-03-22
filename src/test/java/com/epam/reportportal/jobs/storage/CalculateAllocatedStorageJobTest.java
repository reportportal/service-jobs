package com.epam.reportportal.jobs.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static com.epam.reportportal.jobs.storage.CalculateAllocatedStorageJob.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
class CalculateAllocatedStorageJobTest {

	private static final ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private final CalculateAllocatedStorageJob calculateAllocatedStorageJob = new CalculateAllocatedStorageJob(taskExecutor,
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

		when(jdbcTemplate.queryForList(SELECT_PROJECT_IDS, Long.class)).thenReturn(projectIds);
		when(jdbcTemplate.queryForObject(eq(SELECT_FILE_SIZE_SUM_BY_PROJECT_ID), eq(Long.class), anyLong())).thenReturn(1000L);

		calculateAllocatedStorageJob.calculate();

		verify(jdbcTemplate, times(2)).queryForObject(eq(SELECT_FILE_SIZE_SUM_BY_PROJECT_ID), eq(Long.class), anyLong());

		final ArgumentCaptor<Long> projectIdCaptor = ArgumentCaptor.forClass(Long.class);
		verify(jdbcTemplate, times(2)).update(eq(UPDATE_ALLOCATED_STORAGE_BY_PROJECT_ID), anyLong(), projectIdCaptor.capture());
		final List<Long> updatedIds = projectIdCaptor.getAllValues();

		Assertions.assertEquals(projectIds.size(), updatedIds.size());
		Assertions.assertTrue(projectIds.containsAll(updatedIds));
	}

}