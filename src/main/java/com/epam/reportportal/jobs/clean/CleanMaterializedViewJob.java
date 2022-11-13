package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.jobs.BaseJob;
import com.epam.reportportal.model.StaleMaterializedView;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
@Service
public class CleanMaterializedViewJob extends BaseJob {

	private static final String TIME_BOUND_PARAM = "timeBound";
	private static final String BATCH_SIZE_PARAM = "batchSize";

	private static final String NAMES_PARAM = "names";

	private static final String SELECT_STALE_VIEWS = "SELECT id, name FROM stale_materialized_view WHERE creation_date <= :timeBound::TIMESTAMP ORDER BY id LIMIT :batchSize";
	private static final String SELECT_EXISTING_VIEWS_BY_NAME_IN = "SELECT matviewname FROM pg_matviews WHERE matviewname IN (:names)";
	private static final String DELETE_STALE_VIEWS_BY_NAMES = "DELETE FROM stale_materialized_view WHERE name IN (:names)";

	private static final String DROP_MATERIALIZED_VIEW = "DROP MATERIALIZED VIEW IF EXISTS %s";

	private static final Pattern VIEW_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]*$");

	private final Integer batchSize;
	private final Integer liveTimeout;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	public CleanMaterializedViewJob(JdbcTemplate jdbcTemplate, @Value("${rp.environment.variable.clean.view.batch}") Integer batchSize,
			@Value("${rp.environment.variable.clean.view.liveTimeout}") Integer liveTimeout,
			NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
		super(jdbcTemplate);
		this.batchSize = batchSize;
		this.liveTimeout = liveTimeout;
		this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
	}

	@Scheduled(cron = "${rp.environment.variable.clean.view.cron}")
	@SchedulerLock(name = "cleanMaterializedView", lockAtMostFor = "24h")
	public void execute() {
		logStart();
		final AtomicInteger existingCounter = new AtomicInteger(0);
		final AtomicInteger staleCounter = new AtomicInteger(0);

		final LocalDateTime timeBound = LocalDateTime.now(ZoneOffset.UTC).minus(Duration.ofSeconds(liveTimeout));
		List<StaleMaterializedView> staleViews = getStaleViews(timeBound);

		while (!staleViews.isEmpty()) {
			final List<String> viewNames = staleViews.stream()
					.map(StaleMaterializedView::getName)
					.filter(name -> VIEW_NAME_PATTERN.matcher(name).matches())
					.collect(Collectors.toList());

			final int existingRemoved = removeExistingViews(viewNames);
			existingCounter.addAndGet(existingRemoved);

			final int staleRemoved = removeStaleViews(viewNames);
			staleCounter.addAndGet(staleRemoved);

			staleViews = getStaleViews(timeBound);
		}

		logFinish(String.format("Stale removed: %d, Existing removed: %d", staleCounter.get(), existingCounter.get()));

	}

	private List<StaleMaterializedView> getStaleViews(LocalDateTime timeBound) {
		final Map<String, Object> selectParams = Map.of(TIME_BOUND_PARAM, timeBound, BATCH_SIZE_PARAM, batchSize);
		return namedParameterJdbcTemplate.query(SELECT_STALE_VIEWS, selectParams, new BeanPropertyRowMapper<>(StaleMaterializedView.class));
	}

	private int removeExistingViews(List<String> viewNames) {
		final List<String> existingViews = namedParameterJdbcTemplate.queryForList(SELECT_EXISTING_VIEWS_BY_NAME_IN,
				Map.of(NAMES_PARAM, viewNames),
				String.class
		);

		existingViews.forEach(name -> namedParameterJdbcTemplate.update(String.format(DROP_MATERIALIZED_VIEW, name), Map.of()));
		return existingViews.size();
	}

	private int removeStaleViews(List<String> viewNames) {
		return namedParameterJdbcTemplate.update(DELETE_STALE_VIEWS_BY_NAMES, Map.of(NAMES_PARAM, viewNames));
	}
}
