package com.epam.reportportal.jobs.service;

/**
 * @author <a href="mailto:budaevqwerty@gmail.com">Ivan Budayeu</a>
 */
public interface BatchUpdater {

	void updateAll(Integer batchSize);
}
