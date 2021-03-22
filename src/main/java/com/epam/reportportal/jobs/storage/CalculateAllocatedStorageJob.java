package com.epam.reportportal.jobs.storage;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
@Service
public class CalculateAllocatedStorageJob {

	static final String SELECT_PROJECT_IDS = "SELECT id FROM project ORDER BY id";
	static final String SELECT_FILE_SIZE_SUM_BY_PROJECT_ID = "SELECT coalesce(sum(file_size), 0) FROM attachment WHERE attachment.project_id = ?";
	static final String UPDATE_ALLOCATED_STORAGE_BY_PROJECT_ID = "UPDATE project SET allocated_storage = ? WHERE id = ?";

	private final TaskExecutor projectAllocatedStorageExecutor;
	private final JdbcTemplate jdbcTemplate;

	@Autowired
	public CalculateAllocatedStorageJob(TaskExecutor projectAllocatedStorageExecutor, JdbcTemplate jdbcTemplate) {
		this.projectAllocatedStorageExecutor = projectAllocatedStorageExecutor;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Scheduled(cron = "${rp.environment.variable.storage.project.cron}")
	@SchedulerLock(name = "calculateAllocatedStorage", lockAtMostFor = "24h")
	public void calculate() {
		CompletableFuture.allOf(getProjectIds().stream()
				.map(id -> CompletableFuture.runAsync(() -> updateAllocatedStorage(id), projectAllocatedStorageExecutor))
				.toArray(CompletableFuture[]::new)).join();
	}

	private List<Long> getProjectIds() {
		return jdbcTemplate.queryForList(SELECT_PROJECT_IDS, Long.class);
	}

	private void updateAllocatedStorage(Long projectId) {
		final Long allocatedStorage = getAllocatedStorage(projectId);
		updateAllocatedStorage(allocatedStorage, projectId);
	}

	private Long getAllocatedStorage(Long projectId) {
		return jdbcTemplate.queryForObject(SELECT_FILE_SIZE_SUM_BY_PROJECT_ID, Long.class, projectId);
	}

	private void updateAllocatedStorage(Long allocatedStorage, Long projectId) {
		jdbcTemplate.update(UPDATE_ALLOCATED_STORAGE_BY_PROJECT_ID, allocatedStorage, projectId);
	}
}
