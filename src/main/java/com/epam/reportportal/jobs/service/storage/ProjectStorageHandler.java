package com.epam.reportportal.jobs.service.storage;

import com.epam.reportportal.jobs.repository.AttachmentRepository;
import com.epam.reportportal.jobs.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
@Service
public class ProjectStorageHandler implements AllocatedStorageHandler {

	private final ProjectRepository projectRepository;
	private final AttachmentRepository attachmentRepository;

	@Autowired
	public ProjectStorageHandler(ProjectRepository projectRepository,
			AttachmentRepository attachmentRepository) {
		this.projectRepository = projectRepository;
		this.attachmentRepository = attachmentRepository;
	}

	@Override
	public void updateById(Long id) {
		final Long allocatedStorage = attachmentRepository.selectFileSizeSumByProjectId(id);
		projectRepository.updateAllocatedStorageById(allocatedStorage, id);
	}
}
