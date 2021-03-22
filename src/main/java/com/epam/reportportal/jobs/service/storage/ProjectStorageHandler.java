package com.epam.reportportal.jobs.service.storage;

import com.epam.reportportal.jobs.repository.AttachmentRepository;
import com.epam.reportportal.jobs.repository.ProjectRepository;
import com.epam.reportportal.jobs.repository.util.PageableUtils;
import com.epam.reportportal.jobs.service.BatchUpdater;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
@Service
public class ProjectStorageHandler implements AllocatedStorageHandler, BatchUpdater {

	private final TaskExecutor projectAllocatedStorageExecutor;
	private final ProjectRepository projectRepository;
	private final AttachmentRepository attachmentRepository;

	@Autowired
	public ProjectStorageHandler(TaskExecutor projectAllocatedStorageExecutor, ProjectRepository projectRepository,
			AttachmentRepository attachmentRepository) {
		this.projectAllocatedStorageExecutor = projectAllocatedStorageExecutor;
		this.projectRepository = projectRepository;
		this.attachmentRepository = attachmentRepository;
	}

	@Override
	public void updateAllocatedStorage(Long id) {
		final Long allocatedStorage = attachmentRepository.selectFileSizeSumByProjectId(id);
		projectRepository.updateAllocatedStorageById(allocatedStorage, id);
	}

	@Override
	public void updateAll(Integer batchSize) {
		PageableUtils.iterateOverContent(batchSize,
				projectRepository::selectIds,
				ids -> CompletableFuture.allOf(ids.stream()
						.map(id -> CompletableFuture.runAsync(() -> updateAllocatedStorage(id), projectAllocatedStorageExecutor))
						.toArray(CompletableFuture[]::new)).join()
		);
	}
}
