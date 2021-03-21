package com.epam.reportportal.jobs.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.epam.reportportal.jobs.repository.RepositoryConstants.*;

/**
 * @author <a href="mailto:budaevqwerty@gmail.com">Ivan Budayeu</a>
 */
@Repository
public class ProjectRepository {

	private static final String ALLOCATED_STORAGE_PARAM = "allocatedStorage";

	private static final String SELECT_IDS_WITH_LIMIT_AND_OFFSET = "SELECT id FROM project ORDER BY id LIMIT :limitSize OFFSET :offsetSize";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	@Autowired
	public ProjectRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<Long> selectIds(int limit, long offset) {
		final Map<String, Object> queryParams = new HashMap<>();
		queryParams.put(LIMIT_SIZE_PARAM, limit);
		queryParams.put(OFFSET_SIZE_PARAM, offset);

		return jdbcTemplate.queryForList(SELECT_IDS_WITH_LIMIT_AND_OFFSET, queryParams, Long.class);
	}

	public void updateAllocatedStorageById(Long allocatedStorage, Long id) {
		final Map<String, Object> queryParams = new HashMap<>();
		queryParams.put(ALLOCATED_STORAGE_PARAM, allocatedStorage);
		queryParams.put(ID_PARAM, id);

		jdbcTemplate.update("UPDATE project SET allocated_storage = :allocatedStorage WHERE id = :id", queryParams);
	}
}
