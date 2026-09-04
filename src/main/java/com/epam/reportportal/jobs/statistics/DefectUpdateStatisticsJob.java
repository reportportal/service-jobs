/*
 * Copyright 2024 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.jobs.statistics;

import static org.springframework.http.HttpMethod.POST;

import com.epam.reportportal.jobs.BaseJob;
import com.epam.reportportal.model.ga4.AnalyticsDataRecord;
import com.epam.reportportal.model.ga4.AnalyticsMetadata;
import com.epam.reportportal.model.ga4.Ga4Event;
import com.epam.reportportal.model.ga4.Ga4EventParams;
import com.epam.reportportal.model.ga4.Ga4Request;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * Sends statistics about amounts of manual analyzed items to the GA4 service.
 *
 * @author <a href="mailto:maksim_antonov@epam.com">Maksim Antonov</a>
 */
@Service
public class DefectUpdateStatisticsJob extends BaseJob {

  private static final String GA_URL = "https://www.google-analytics.com/mp/collect?measurement_id=%s&api_secret=%s";
  private static final String DATE_BEFORE = "date_before";

  private static final String SELECT_INSTANCE_ID_QUERY = "SELECT value FROM server_settings WHERE key = 'server.details.instance';";
  private static final String SELECT_STATISTICS_QUERY = "SELECT * FROM analytics_data WHERE type = 'DEFECT_UPDATE_STATISTICS' AND created_at >= :date_before::TIMESTAMP;";
  private static final String DELETE_STATISTICS_QUERY = "DELETE FROM analytics_data WHERE type = 'DEFECT_UPDATE_STATISTICS';";

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  private final RestTemplate restTemplate;

  private final ObjectMapper objectMapper;

  private final String mId;
  private final String gaId;


  /**
   * Initializes {@link DefectUpdateStatisticsJob}.
   *
   * @param jdbcTemplate {@link JdbcTemplate}
   */
  @Autowired
  public DefectUpdateStatisticsJob(JdbcTemplate jdbcTemplate,
      @Value("${rp.environment.variable.ga.mId}") String mId,
      @Value("${rp.environment.variable.ga.id}") String gaId,
      NamedParameterJdbcTemplate namedParameterJdbcTemplate,
      ObjectMapper objectMapper) {
    super(jdbcTemplate);
    this.mId = mId;
    this.gaId = gaId;
    this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    this.objectMapper = objectMapper;
    this.restTemplate = new RestTemplate();
  }


  /**
   * Sends analyzed items statistics.
   */
  @Override
  @Scheduled(cron = "${rp.environment.variable.ga.cron}")
  @SchedulerLock(name = "defectUpdateStatisticsJob", lockAtMostFor = "24h")
  @Transactional
  public void execute() {
    LOGGER.info("Start sending items defect update statistics");
    if (StringUtils.isEmpty(mId) || StringUtils.isEmpty(gaId)) {
      LOGGER.info(
          "Both 'mId' and 'id' environment variables should be provided in order to run the job 'defectUpdateStatisticsJob'");
      return;
    }

    var now = Instant.now();
    var dateBefore = now.minus(1, ChronoUnit.DAYS)
        .atOffset(ZoneOffset.UTC)
        .toLocalDateTime();
    MapSqlParameterSource queryParams = new MapSqlParameterSource();
    queryParams.addValue(DATE_BEFORE, dateBefore);

    List<AnalyticsMetadata> statistics = namedParameterJdbcTemplate.query(SELECT_STATISTICS_QUERY,
        queryParams, (rs, rowNum) -> readMetadata(rs.getString("metadata")));

    if (!statistics.isEmpty()) {
      var instanceId = jdbcTemplate.queryForObject(SELECT_INSTANCE_ID_QUERY, String.class);
      try {
        statistics.stream()
            .collect(Collectors.groupingBy(AnalyticsMetadata::getOrganizationId))
            .forEach((organizationId, orgStatistics) ->
                sendRequest(buildRequestBody(now, instanceId, organizationId, orgStatistics)));
      } finally {
        jdbcTemplate.execute(DELETE_STATISTICS_QUERY);
      }
    }

    LOGGER.info("Completed items defect update statistics job");

  }

  private AnalyticsMetadata readMetadata(String metadata) {
    try {
      return objectMapper.readValue(metadata, AnalyticsDataRecord.class).getMetadata();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to parse analytics data metadata", e);
    }
  }

  private Ga4Request buildRequestBody(Instant now, String instanceId, Long organizationId,
      List<AnalyticsMetadata> statistics) {
    var summary = DefectAnalysisSummary.of(statistics);

    var params = Ga4EventParams.builder()
        .organizationId(organizationId == null ? null : organizationId + "|" + instanceId)
        .category("analyzer")
        .instanceId(instanceId)
        .timestamp(now.toEpochMilli())
        .version(summary.version)
        .type(summary.analyzerEnabled ? "is_analyzer" : "not_analyzer")
        .number(summary.analyzerEnabled ? summary.formatCounts() : null)
        .autoAnalysis(summary.analyzerEnabled ? String.join("#", summary.autoAnalysisState) : null)
        .status(summary.analyzerEnabled ? String.join("#", summary.status) : null)
        .build();

    var event = Ga4Event.builder()
        .name("analyze_analyzer")
        .params(params)
        .build();

    return Ga4Request.builder()
        .clientId(now.toEpochMilli() + "." + new SecureRandom().nextInt(100_000, 999_999))
        .events(List.of(event))
        .build();
  }

  /**
   * Accumulates per-organization defect analysis counters across all {@link AnalyticsMetadata}
   * rows collected for that organization.
   */
  private static final class DefectAnalysisSummary {

    private int analyzed;
    private int userAnalyzed;
    private int sentToAnalyze;
    private int skipped;
    private String version;
    private boolean analyzerEnabled;
    private final Set<String> status = new HashSet<>();
    private final Set<String> autoAnalysisState = new HashSet<>();

    static DefectAnalysisSummary of(List<AnalyticsMetadata> statistics) {
      var summary = new DefectAnalysisSummary();
      statistics.forEach(summary::accumulate);
      return summary;
    }

    private void accumulate(AnalyticsMetadata metadata) {
      analyzerEnabled = metadata.isAnalyzerEnabled();
      if (analyzerEnabled) {
        autoAnalysisState.add(metadata.isAutoAnalysisOn() ? "on" : "off");
      }

      if (metadata.getUserAnalyzed() > 0) {
        status.add("manually");
        sentToAnalyze += metadata.getUserAnalyzed();
      } else {
        status.add("automatically");
        sentToAnalyze += metadata.getSentToAnalyze();
      }
      skipped += metadata.getSkipped();

      userAnalyzed += metadata.getUserAnalyzed();
      analyzed += metadata.getAnalyzed();
      version = metadata.getVersion();
    }

    private String formatCounts() {
      return analyzed + "#" + userAnalyzed + "#" + sentToAnalyze + "#" + skipped;
    }
  }

  private void sendRequest(Ga4Request requestBody) {
    try {
      String body = objectMapper.writeValueAsString(requestBody);
      LOGGER.debug("Sending statistics data: {}", body);

      var response = restTemplate.exchange(gaCollectUrl(), POST, asJsonEntity(body), String.class);
      if (!response.getStatusCode().equals(HttpStatus.NO_CONTENT)) {
        LOGGER.error("Failed to send statistics: {}", response);
      }
    } catch (Exception e) {
      LOGGER.error("Failed to send statistics", e);
    }
  }

  private String gaCollectUrl() {
    return String.format(GA_URL, mId, gaId);
  }

  private HttpEntity<String> asJsonEntity(String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

}
