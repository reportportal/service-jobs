package com.epam.reportportal.elastic;

import com.epam.reportportal.log.LogMessage;

import java.util.List;

/**
 * Client interface to work with Elasticsearch.
 * @author <a href="mailto:maksim_antonov@epam.com">Maksim Antonov</a>
 */
public interface ElasticSearchClient {

    void save(LogMessage logMessage);

    void save(List<LogMessage> logMessageList);

    void deleteStreamByLaunchIdAndProjectId(Long launchId, Long projectId);
}
