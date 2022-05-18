package com.epam.reportportal.elastic;

import com.epam.reportportal.log.LogMessage;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SimpleElasticSearchClient {
    private final String host;
    private final RestTemplate restTemplate;

    public SimpleElasticSearchClient(@Value("${rp.elasticsearch.host}") String host,
                                     @Value("${rp.elasticsearch.username}") String username,
                                     @Value("${rp.elasticsearch.password}") String password) {
        restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(username, password));

        this.host = host;
    }

    public void save(LogMessage logMessage) {
        Long launchId = logMessage.getLaunchId();
        String indexName = "logs-reportportal-" + logMessage.getProjectId() + "-" + launchId;

        JSONObject personJsonObject = convertToJson(logMessage);

        HttpEntity<String> request = getStringHttpEntity(personJsonObject.toString());

        restTemplate.postForObject(host + "/" + indexName + "/_doc", request, String.class);
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
