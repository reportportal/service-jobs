package com.epam.reportportal.jobs.storage;

import com.epam.reportportal.jobs.service.project.ProjectService;
import com.epam.reportportal.jobs.service.storage.AllocatedStorageHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
class CalculateAllocatedStorageJobTest {

	private static final ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
	private final ProjectService projectService = mock(ProjectService.class);
	private final AllocatedStorageHandler allocatedStorageHandler = mock(AllocatedStorageHandler.class);

	private final CalculateAllocatedStorageJob calculateAllocatedStorageJob = new CalculateAllocatedStorageJob(taskExecutor,
			projectService,
			allocatedStorageHandler
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

		when(projectService.getAllIds()).thenReturn(projectIds);

		calculateAllocatedStorageJob.calculate();

		verify(allocatedStorageHandler, times(2)).updateById(anyLong());
	}

}