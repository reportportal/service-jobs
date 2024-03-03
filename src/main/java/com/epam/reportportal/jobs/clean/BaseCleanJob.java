package com.epam.reportportal.jobs.clean;

import static java.time.Duration.ofSeconds;

import com.epam.reportportal.jobs.BaseJob;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public abstract class BaseCleanJob extends BaseJob {

  protected static final String KEEP_LAUNCHES = "job.keepLaunches";
  protected static final String KEEP_LOGS = "job.keepLogs";
  protected static final String KEEP_SCREENSHOTS = "job.keepScreenshots";

  protected final String SELECT_PROJECTS_ATTRIBUTES =
      """
          SELECT pa.project_id AS id, pa.value AS attribute_value FROM project_attribute pa\s
          JOIN attribute a ON pa.attribute_id = a.id WHERE a.name = ? AND pa.value != '0' AND TRIM(pa.value) != '';""";

  public BaseCleanJob(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  protected Map<Long, Duration> getProjectsWithAttribute(String attributeKey) {
    return jdbcTemplate.query(SELECT_PROJECTS_ATTRIBUTES, rs -> {
      Map<Long, Duration> result = new HashMap<>();
      while (rs.next()) {
        String attributeValue = rs.getString("attribute_value");
        try {
          result.put(rs.getLong("id"), ofSeconds(Long.parseLong(attributeValue)));
        } catch (NumberFormatException e) {
          LOGGER.error("Bad attribute value format for {}. Expected a number, actual is {}",
              attributeKey, attributeValue);
        }
      }
      return result;
    }, attributeKey);
  }
}
