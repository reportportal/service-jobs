package com.epam.reportportal.elastic;

import com.epam.reportportal.log.LogMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Simple client to work with Search engine.
 *
 * @author <a href="mailto:maksim_antonov@epam.com">Maksim Antonov</a>
 */
@Primary
@Service
@ConditionalOnProperty(prefix = "rp.searchengine", name = "host")
public class SimpleSearchEngineClient implements SearchEngineClient {

  protected final Logger LOGGER = LoggerFactory.getLogger(SimpleSearchEngineClient.class);

  private final String host;
  private final RestTemplate restTemplate;

  public SimpleSearchEngineClient(@Value("${rp.searchengine.host}") String host,
      @Value("${rp.searchengine.username:}") String username,
      @Value("${rp.searchengine.password:}") String password) {
    restTemplate = new RestTemplate();

    if (!username.isEmpty() && !password.isEmpty()) {
      restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(username, password));
    }

    this.host = host;
  }

  @Override
  public void save(List<LogMessage> logMessageList) {
    if (CollectionUtils.isEmpty(logMessageList)) {
      return;
    }
    Map<String, String> logsByIndex = new HashMap<>();

    String create = "{\"create\":{ }}\n";

    logMessageList.forEach(logMessage -> {
      String indexName = "logs-reportportal-" + logMessage.getProjectId();
      String logCreateBody = create + convertToJson(logMessage) + "\n";

      if (logsByIndex.containsKey(indexName)) {
        logsByIndex.put(indexName, logsByIndex.get(indexName) + logCreateBody);
      } else {
        logsByIndex.put(indexName, logCreateBody);
      }
    });

    logsByIndex.forEach(
        (indexName, body) -> restTemplate.put(host + "/" + indexName + "/_bulk?refresh",
            getStringHttpEntity(body)
        ));
  }

  @Override
  public void deleteLogsByLaunchIdAndProjectId(Long launchId, Long projectId) {
    String indexName = "logs-reportportal-" + projectId;
    try {
      JSONObject deleteByLaunch = getDeleteLaunchJson(launchId);
      HttpEntity<String> deleteRequest = getStringHttpEntity(deleteByLaunch.toString());

      restTemplate.postForObject(host + "/" + indexName + "/_delete_by_query", deleteRequest,
          JSONObject.class
      );
    } catch (Exception exception) {
      // to avoid checking of exists stream or not
      LOGGER.info("DELETE logs from stream ES error " + indexName + " " + exception.getMessage());
    }
  }

  private JSONObject getDeleteLaunchJson(Long launchId) {
    JSONObject match = new JSONObject();
    match.put("launchId", launchId);

    JSONObject query = new JSONObject();
    query.put("match", match);

    JSONObject deleteByLaunch = new JSONObject();
    deleteByLaunch.put("query", query);

    return deleteByLaunch;
  }

  private JSONObject convertToJson(LogMessage logMessage) {
    JSONObject personJsonObject = new JSONObject();
    personJsonObject.put("id", logMessage.getId());
    personJsonObject.put("message", logMessage.getLogMessage());
    personJsonObject.put("itemId", logMessage.getItemId());
    personJsonObject.put("@timestamp", logMessage.getLogTime());
    personJsonObject.put("launchId", logMessage.getLaunchId());

    return personJsonObject;
  }

  private HttpEntity<String> getStringHttpEntity(String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    return new HttpEntity<>(body, headers);
  }
}
