package com.epam.reportportal.jobs.storage;

import com.epam.reportportal.jobs.service.BatchUpdater;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
@Service
public class CalculateAllocatedStorageJob {

	private final Integer projectPageSize;
	private final BatchUpdater projectStorageHandler;

	public CalculateAllocatedStorageJob(@Value("${rp.environment.variable.storage.project.pageSize}") Integer projectPageSize,
			BatchUpdater projectStorageHandler) {
		this.projectPageSize = projectPageSize;
		this.projectStorageHandler = projectStorageHandler;
	}

	@Scheduled(cron = "${rp.environment.variable.storage.project.cron}")
	@SchedulerLock(name = "calculateAllocatedStorage", lockAtMostFor = "24h")
	public void calculate() {
		projectStorageHandler.updateAll(projectPageSize);
	}
}
