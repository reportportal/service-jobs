package com.epam.reportportal.elastic;

import com.epam.reportportal.log.LogMessage;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Empty client to work with Elasticsearch.
 *
 * @author <a href="mailto:maksim_antonov@epam.com">Maksim Antonov</a>
 */
@Service
public class EmptyElasticSearchClient implements ElasticSearchClient {

  @Override
  public void save(List<LogMessage> logMessageList) {
  }

  @Override
  public void deleteLogsByLaunchIdAndProjectId(Long launchId, Long projectId) {
  }
}
