package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.jobs.BaseJob;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class BaseCleanJob extends BaseJob {

	protected static final String KEEP_LAUNCHES = "job.keepLaunches";
	protected static final String KEEP_LOGS = "job.keepLogs";
	protected static final String KEEP_SCREENSHOTS = "job.keepScreenshots";

	protected final String SELECT_PROJECTS_ATTRIBUTES = "SELECT p.id AS id, pa.value::BIGINT AS attribute_value FROM project p "
			+ "JOIN project_attribute pa ON p.id = pa.project_id JOIN attribute a ON pa.attribute_id = a.id "
			+ "WHERE a.name = ? AND pa.value != '0';";

	public BaseCleanJob(JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}

	protected Map<Long, Long> getProjectsWithAttribute(String attributeKey) {
		return jdbcTemplate.query(SELECT_PROJECTS_ATTRIBUTES, rs -> {
			Map<Long, Long> result = new HashMap<>();
			while (rs.next()) {
				result.put(rs.getLong("id"), rs.getLong("attribute_value"));
			}
			return result;
		}, attributeKey);
	}
}
