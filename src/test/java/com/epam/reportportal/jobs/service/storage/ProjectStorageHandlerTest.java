package com.epam.reportportal.jobs.service.storage;

import com.epam.reportportal.jobs.repository.AttachmentRepository;
import com.epam.reportportal.jobs.repository.ProjectRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
class ProjectStorageHandlerTest {

	private static ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
	private final ProjectRepository projectRepository = mock(ProjectRepository.class);
	private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);

	private final ProjectStorageHandler projectStorageHandler = new ProjectStorageHandler(taskExecutor,
			projectRepository,
			attachmentRepository
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
	void shouldUpdateAllocatedStorage() {
		final Long projectId = 1L;
		final Long storageValue = 1000L;

		when(attachmentRepository.selectFileSizeSumByProjectId(anyLong())).thenReturn(storageValue);

		projectStorageHandler.updateAllocatedStorage(projectId);

		ArgumentCaptor<Long> allocatedStorageCaptor = ArgumentCaptor.forClass(Long.class);
		verify(projectRepository, times(1)).updateAllocatedStorageById(allocatedStorageCaptor.capture(), eq(projectId));

		Assertions.assertEquals(storageValue, allocatedStorageCaptor.getValue());

	}

	@Test
	void shouldUpdateAllocatedStorageForAllProjects() {

		final List<Long> projectIds = List.of(1L, 2L);

		when(projectRepository.selectIds(2, 0)).thenReturn(projectIds);

		projectStorageHandler.updateAll(2);

		verify(attachmentRepository, times(2)).selectFileSizeSumByProjectId(anyLong());
		verify(projectRepository, times(2)).updateAllocatedStorageById(anyLong(), anyLong());
	}

}