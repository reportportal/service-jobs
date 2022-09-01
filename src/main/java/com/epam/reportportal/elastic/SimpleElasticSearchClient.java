package com.epam.reportportal.elastic;

import com.epam.reportportal.log.LogMessage;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple client to work with Elasticsearch.
 * @author <a href="mailto:maksim_antonov@epam.com">Maksim Antonov</a>
 */
@Primary
@Service
@ConditionalOnProperty(prefix = "rp.elasticsearch", name = "host")
public class SimpleElasticSearchClient implements ElasticSearchClient {

    protected final Logger LOGGER = LoggerFactory.getLogger(SimpleElasticSearchClient.class);

    private final String host;
    private final RestTemplate restTemplate;

    public SimpleElasticSearchClient(@Value("${rp.elasticsearch.host}") String host,
                                     @Value("${rp.elasticsearch.username}") String username,
                                     @Value("${rp.elasticsearch.password}") String password) {
        restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(username, password));

        this.host = host;
    }

    @Override
    public void save(LogMessage logMessage) {
        Long launchId = logMessage.getLaunchId();
        String indexName = "logs-reportportal-" + logMessage.getProjectId() + "-" + launchId;

        JSONObject personJsonObject = convertToJson(logMessage);

        HttpEntity<String> request = getStringHttpEntity(personJsonObject.toString());

        restTemplate.postForObject(host + "/" + indexName + "/_doc", request, String.class);
    }

    @Override
    public void save(List<LogMessage> logMessageList) {
        if (CollectionUtils.isEmpty(logMessageList)) return;
        Map<String, String> logsByIndex = new HashMap<>();

        String create = "{\"create\":{ }}\n";

        logMessageList.forEach(logMessage -> {
            String indexName = "logs-reportportal-" + logMessage.getProjectId() + "-" + logMessage.getLaunchId();
            String logCreateBody = create + convertToJson(logMessage) + "\n";

            if (logsByIndex.containsKey(indexName)) {
                logsByIndex.put(indexName, logsByIndex.get(indexName) + logCreateBody);
            } else {
                logsByIndex.put(indexName, logCreateBody);
            }
        });

        logsByIndex.forEach((indexName, body) -> {
            restTemplate.put(host + "/" + indexName + "/_bulk?refresh", getStringHttpEntity(body));
        });
    }

    @Override
    public void deleteStreamByLaunchIdAndProjectId(Long launchId, Long projectId) {
        String indexName = "logs-reportportal-" + projectId + "-" + launchId;
        try {
            restTemplate.delete(host + "/_data_stream/" + indexName);
        } catch (Exception exception) {
            // to avoid checking of exists stream or not
            LOGGER.info("DELETE stream from ES error " + indexName + " " + exception.getMessage());
        }
    }

    private JSONObject convertToJson(LogMessage logMessage) {
        JSONObject personJsonObject = new JSONObject();
        personJsonObject.put("id", logMessage.getId());
        personJsonObject.put("message", logMessage.getLogMessage());
        personJsonObject.put("itemId", logMessage.getItemId());
        personJsonObject.put("@timestamp", logMessage.getLogTime());

        return personJsonObject;
    }

    private HttpEntity<String> getStringHttpEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(body, headers);
    }
}
