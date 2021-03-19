package com.epam.reportportal.old_jobs.clean;

import com.epam.reportportal.old_jobs.entity.Project;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public interface CleanupService {

	void clean(Project project);

	int order();
}
