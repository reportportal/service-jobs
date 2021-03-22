package com.epam.reportportal.jobs.storage;

import com.epam.reportportal.jobs.service.project.ProjectService;
import com.epam.reportportal.jobs.service.storage.AllocatedStorageHandler;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
@Service
public class CalculateAllocatedStorageJob {

	private final TaskExecutor projectAllocatedStorageExecutor;
	private final ProjectService projectService;
	private final AllocatedStorageHandler projectStorageHandler;

	@Autowired
	public CalculateAllocatedStorageJob(TaskExecutor projectAllocatedStorageExecutor, ProjectService projectService,
			AllocatedStorageHandler projectStorageHandler) {
		this.projectAllocatedStorageExecutor = projectAllocatedStorageExecutor;
		this.projectService = projectService;
		this.projectStorageHandler = projectStorageHandler;
	}

	@Scheduled(cron = "${rp.environment.variable.storage.project.cron}")
	@SchedulerLock(name = "calculateAllocatedStorage", lockAtMostFor = "24h")
	public void calculate() {
		CompletableFuture.allOf(projectService.getAllIds()
				.stream()
				.map(id -> CompletableFuture.runAsync(() -> projectStorageHandler.updateById(id), projectAllocatedStorageExecutor))
				.toArray(CompletableFuture[]::new)).join();
	}
}
