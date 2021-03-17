package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.jobs.entity.Attribute;
import com.epam.reportportal.jobs.entity.Project;
import com.epam.reportportal.jobs.entity.ProjectAttributeEnum;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class CleanProjectJob {

	private static final String SELECT_PROJECTS_ATTRIBUTES =
			"SELECT p.id AS id, p.name AS name, a.name AS attribute, pa.value AS attribute_value " + "FROM project p "
					+ "JOIN project_attribute pa ON p.id = pa.project_id "
					+ "JOIN attribute a ON pa.attribute_id = a.id WHERE a.name IN (:names);";

	private final List<CleanupService> cleanupServices;

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public CleanProjectJob(List<CleanupService> cleanupServices, NamedParameterJdbcTemplate jdbcTemplate) {
		this.cleanupServices = cleanupServices;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Scheduled(cron = "${rp.environment.variable.clean.project.cron}")
	@SchedulerLock(name = "cleanProject", lockAtMostFor = "24h")
	public void execute() {
		List<Project> projects = jdbcTemplate.query(SELECT_PROJECTS_ATTRIBUTES,
				new MapSqlParameterSource().addValue("names",
						Arrays.stream(ProjectAttributeEnum.values()).map(ProjectAttributeEnum::getAttribute).collect(Collectors.toList())
				),
				resultSetExtractor()
		);

		if (projects != null) {
			projects.forEach(project -> cleanupServices.stream()
					.sorted(Comparator.comparingInt(CleanupService::order))
					.forEach(it -> it.clean(project)));
		}
	}

	/**
	 * Extracts project entities with attributes from result set
	 * @return ResultSetExtractor
	 */
	private ResultSetExtractor<List<Project>> resultSetExtractor() {
		return rs -> {
			Map<Long, Project> projectMap = new HashMap<>();
			while (rs.next()) {
				long projectId = rs.getLong("id");
				Project project;
				if (projectMap.containsKey(projectId)) {
					project = projectMap.get(projectId);
				} else {
					project = new Project();
					project.setId(projectId);
					project.setName(rs.getString("name"));
				}
				project.getAttributes().add(new Attribute(rs.getString("attribute"), rs.getString("attribute_value")));
				projectMap.put(projectId, project);
			}
			return new ArrayList<>(projectMap.values());
		};
	}
}
