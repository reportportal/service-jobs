package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.jobs.entity.EntityUtils;
import com.epam.reportportal.jobs.entity.Project;
import com.epam.reportportal.jobs.entity.ProjectAttributeEnum;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static java.time.Duration.ofSeconds;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanAttachmentsService implements CleanupService {

	private static final Logger LOGGER = LoggerFactory.getLogger(CleanAttachmentsService.class);

	private static final String MOVING_QUERY = "WITH moved_rows AS (DELETE FROM attachment WHERE project_id = :id AND creation_date <= :date::TIMESTAMP RETURNING *) INSERT INTO attachment_tombstone SELECT * FROM moved_rows;";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public CleanAttachmentsService(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void clean(Project project) {
		LOGGER.info("Cleaning outdated attachments has been started for project {}", project.getName());
		EntityUtils.extractAttributeValue(project, ProjectAttributeEnum.KEEP_SCREENSHOTS.getAttribute())
				.map(keepPeriod -> ofSeconds(NumberUtils.toLong(keepPeriod, 0L)))
				.filter(keepPeriod -> !keepPeriod.isZero())
				.map(keepPeriod -> LocalDateTime.now(ZoneOffset.UTC).minus(keepPeriod))
				.ifPresent(lessThanDate -> moveToTombstone(project.getId(), lessThanDate));
	}

	@Override
	public int order() {
		return 0;
	}

	/**
	 * Moves attachments to the tombstone table for further deleting by a different job
	 *
	 * @param projectId    Project id
	 * @param lessThanDate Attachment less than provided date must be moved
	 */
	private void moveToTombstone(Long projectId, LocalDateTime lessThanDate) {
		int moved = jdbcTemplate.update(MOVING_QUERY, new MapSqlParameterSource().addValue("id", projectId).addValue("date", lessThanDate));
		LOGGER.info("Moved {} attachments to the tombstone table", moved);
	}
}
