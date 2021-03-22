package com.epam.reportportal.jobs.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

import static com.epam.reportportal.jobs.repository.RepositoryConstants.PROJECT_ID_PARAM;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
@Repository
public class AttachmentRepository {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public AttachmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Long selectFileSizeSumByProjectId(Long projectId) {
		final Map<String, Object> queryParams = new HashMap<>();
		queryParams.put(PROJECT_ID_PARAM, projectId);

		return jdbcTemplate.queryForObject("SELECT coalesce(sum(file_size), 0) FROM attachment WHERE attachment.project_id = :projectId",
				queryParams,
				Long.class
		);
	}
}
