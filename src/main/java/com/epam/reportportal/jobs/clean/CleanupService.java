package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.jobs.entity.Project;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public interface CleanupService {

	void clean(Project project);

	int order();
}
