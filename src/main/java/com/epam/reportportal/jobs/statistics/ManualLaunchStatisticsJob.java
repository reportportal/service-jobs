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
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
public class ManualLaunchStatisticsJob extends BaseJob {

  private static final String GA_URL = "https://www.google-analytics.com/mp/collect?measurement_id=%s&api_secret=%s";
  private static final String DATE_BEFORE = "date_before";

  private static final String SELECT_INSTANCE_ID_QUERY = "SELECT value FROM server_settings WHERE key = 'server.details.instance';";
  private static final String SELECT_ANALYZER_MANUAL_START_QUERY = "SELECT * FROM analytics_data WHERE type = 'ANALYZER_MANUAL_START' AND created_at >= :date_before::TIMESTAMP;";
  private static final String DELETE_ANALYZER_MANUAL_START_QUERY = "DELETE FROM analytics_data WHERE type = 'ANALYZER_MANUAL_START';";

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  private final RestTemplate restTemplate;

  private final String measurementId;
  private final String apiSecret;


  /**
   * Initializes {@link ManualLaunchStatisticsJob}.
   *
   * @param jdbcTemplate {@link JdbcTemplate}
   */
  @Autowired
  public ManualLaunchStatisticsJob(JdbcTemplate jdbcTemplate,
      @Value("${rp.environment.variable.ga.measurementId}") String measurementId,
      @Value("${rp.environment.variable.ga.apiSecret}") String apiSecret,
      NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
    super(jdbcTemplate);
    this.measurementId = measurementId;
    this.apiSecret = apiSecret;
    this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    this.restTemplate = new RestTemplate();
  }


  /**
   * Sends manual analyzed items statistics.
   */
  @Override
  @Scheduled(cron = "${rp.environment.variable.ga.cron}")
  @SchedulerLock(name = "manualAnalyzedStatisticsJob", lockAtMostFor = "24h")
  @Transactional
  public void execute() {
    LOGGER.info("Start sending analyzer manual start item statistics");
    if (StringUtils.isEmpty(measurementId) || StringUtils.isEmpty(apiSecret)) {
      LOGGER.info(
          "Both 'measurementId' and 'apiSecret' environment variables should be provided in order to run the job 'manualAnalyzedStatisticsJob'");
      return;
    }

    var now = Instant.now();
    var dateBefore = now.minus(1, ChronoUnit.DAYS)
        .atOffset(ZoneOffset.UTC)
        .toLocalDateTime();
    MapSqlParameterSource queryParams = new MapSqlParameterSource();
    queryParams.addValue(DATE_BEFORE, dateBefore);

    namedParameterJdbcTemplate.query(SELECT_ANALYZER_MANUAL_START_QUERY, queryParams, rs -> {
      int autoAnalyzed = 0;
      int userAnalyzed = 0;
      int sentToAnalyze = 0;
      String version = null;
      boolean analyzerEnabled;
      Set<String> status = new HashSet<>();
      Set<String> autoAnalysisState = new HashSet<>();

      do {
        var metadata = new JSONObject(rs.getString("metadata"))
            .getJSONObject("metadata");

        analyzerEnabled = metadata.getBoolean("analyzerEnabled");
        if (analyzerEnabled) {
          autoAnalysisState.add(metadata.getBoolean("autoAnalysisOn") ? "on" : "off");
        }

        if (metadata.optInt("userAnalyzed") > 0) {
          status.add("manually");
        } else if (metadata.optInt("autoAnalyzed") > 0) {
          status.add("automatically");
        }

        userAnalyzed += metadata.optInt("userAnalyzed");
        autoAnalyzed += metadata.optInt("autoAnalyzed");
        sentToAnalyze += metadata.optInt("userAnalyzed") + metadata.optInt("sentToAnalyze");
        version = metadata.getString("version");

      } while (rs.next());

      var instanceId = jdbcTemplate.queryForObject(SELECT_INSTANCE_ID_QUERY, String.class);
      var params = new JSONObject();
      params.put("category", "analyzer");
      params.put("instanceID", instanceId);
      params.put("timestamp", now.toEpochMilli());
      params.put("version", version);
      params.put("type", analyzerEnabled ? "is_analyzer" : "not_analyzer");
      if (analyzerEnabled) {
        params.put("number", autoAnalyzed + "#" + userAnalyzed + "#" + sentToAnalyze);
        params.put("auto_analysis", String.join("#", autoAnalysisState));
        params.put("status", String.join("#", status));
      }

      var event = new JSONObject();
      event.put("name", "analyze_analyzer");
      event.put("params", params);

      JSONArray events = new JSONArray();
      events.put(event);

      JSONObject requestBody = new JSONObject();
      requestBody.put("client_id",
          now.toEpochMilli() + "." + new SecureRandom().nextInt(100_000, 999_999));
      requestBody.put("events", events);

      sendRequest(requestBody);

    });

    LOGGER.info("Completed analyzer manual start item statistics job");

  }

  private void sendRequest(JSONObject requestBody) {
    try {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Sending statistics data: {}", requestBody);
      }

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);

      String url = String.format(GA_URL, measurementId, apiSecret);

      var response = restTemplate.exchange(url, POST, request, String.class);
      if (response.getStatusCodeValue() != 204) {
        LOGGER.error("Failed to send statistics: {}", response);
      }
    } catch (Exception e) {
      LOGGER.error("Failed to send statistics", e);
    } finally {
      jdbcTemplate.execute(DELETE_ANALYZER_MANUAL_START_QUERY);
    }
  }

}
