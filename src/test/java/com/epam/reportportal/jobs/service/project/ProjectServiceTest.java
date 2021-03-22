package com.epam.reportportal.jobs.service.project;

import com.epam.reportportal.jobs.repository.ProjectRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
class ProjectServiceTest {

	private final ProjectRepository projectRepository = mock(ProjectRepository.class);
	private final ProjectService projectService = new ProjectService(projectRepository);

	@Test
	void shouldSelectIds() {
		projectService.getAllIds();
		verify(projectRepository, times(1)).selectIds();
	}

	@Test
	void shouldReturnSelectedIds() {

		final List<Long> ids = List.of(1L, 2L, 3L);
		when(projectRepository.selectIds()).thenReturn(ids);

		final List<Long> allIds = projectService.getAllIds();

		Assertions.assertEquals(ids, allIds);
	}

}