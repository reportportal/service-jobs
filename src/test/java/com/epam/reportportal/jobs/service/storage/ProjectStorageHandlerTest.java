package com.epam.reportportal.jobs.service.storage;

import com.epam.reportportal.jobs.repository.AttachmentRepository;
import com.epam.reportportal.jobs.repository.ProjectRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
class ProjectStorageHandlerTest {

	private final ProjectRepository projectRepository = mock(ProjectRepository.class);
	private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);

	private final ProjectStorageHandler projectStorageHandler = new ProjectStorageHandler(projectRepository, attachmentRepository);

	@Test
	void shouldUpdateAllocatedStorage() {
		final Long projectId = 1L;
		final Long storageValue = 1000L;

		when(attachmentRepository.selectFileSizeSumByProjectId(anyLong())).thenReturn(storageValue);

		projectStorageHandler.updateById(projectId);

		ArgumentCaptor<Long> allocatedStorageCaptor = ArgumentCaptor.forClass(Long.class);
		verify(projectRepository, times(1)).updateAllocatedStorageById(allocatedStorageCaptor.capture(), eq(projectId));

		Assertions.assertEquals(storageValue, allocatedStorageCaptor.getValue());

	}

}