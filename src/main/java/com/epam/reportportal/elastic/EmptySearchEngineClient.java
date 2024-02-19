package com.epam.reportportal.elastic;

import com.epam.reportportal.log.LogMessage;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Empty client to work with Search engine.
 *
 * @author <a href="mailto:maksim_antonov@epam.com">Maksim Antonov</a>
 */
@Service
public class EmptySearchEngineClient implements SearchEngineClient {

  @Override
  public void save(List<LogMessage> logMessageList) {
  }

  @Override
  public void deleteLogsByLaunchIdAndProjectId(Long launchId, Long projectId) {
  }
}
