package com.epam.reportportal.jobs.service;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
public interface BatchUpdater {

	void updateAll(Integer batchSize);
}
