package com.epam.reportportal.elastic;

import com.epam.reportportal.log.LogMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Empty client to work with Elasticsearch.
 * @author <a href="mailto:maksim_antonov@epam.com">Maksim Antonov</a>
 */
@Service
public class EmptyElasticSearchClient implements ElasticSearchClient  {

    @Override
    public void save(LogMessage logMessage) {
    }

    @Override
    public void save(List<LogMessage> logMessageList) {
    }

    @Override
    public void deleteStreamByLaunchIdAndProjectId(Long launchId, Long projectId) {
    }
}
