package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.jobs.entity.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanLaunchesService implements CleanupService {

	private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void clean(Project project) {
		LOGGER.info("Cleaning outdated logs has been started for project {}", project.getName());
		AtomicInteger integer = new AtomicInteger();
		LOGGER.info("{} outdated logs removed for project {}", integer.get(), project.getName());
	}

	@Override
	public int order() {
		return 2;
	}
}
