package com.epam.reportportal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
@Configuration
public class ExecutorConfig {

	@Bean
	public TaskExecutor projectAllocatedStorageExecutor(@Value("${rp.environment.variable.executor.pool.storage.project.core}") Integer corePoolSize,
			@Value("${rp.environment.variable.executor.pool.storage.project.max}") Integer maxPoolSize) {
		final ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
		threadPoolTaskExecutor.setCorePoolSize(corePoolSize);
		threadPoolTaskExecutor.setMaxPoolSize(maxPoolSize);
		threadPoolTaskExecutor.setAllowCoreThreadTimeOut(true);
		threadPoolTaskExecutor.setThreadNamePrefix("prj-alloc-storage");
		return threadPoolTaskExecutor;
	}
}
