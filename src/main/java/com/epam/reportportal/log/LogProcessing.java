package com.epam.reportportal.log;

import com.epam.reportportal.calculation.BatchProcessing;
import com.epam.reportportal.elastic.ElasticSearchClient;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.DefaultManagedTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * Batch processing for log.
 *
 * @author <a href="mailto:maksim_antonov@epam.com">Maksim Antonov</a>
 */
@Component
@ConditionalOnProperty(prefix = "rp.es", name = "host")
public class LogProcessing extends BatchProcessing<LogMessage> {

  private final ElasticSearchClient elasticSearchClient;

  public LogProcessing(ElasticSearchClient elasticSearchClient,
      @Value("${rp.processing.log.maxBatchSize}") int batchSize,
      @Value("${rp.processing.log.maxBatchTimeout}") int timeout) {
    super(batchSize, timeout, new DefaultManagedTaskScheduler());
    this.elasticSearchClient = elasticSearchClient;
  }

  @Override
  protected void process(List<LogMessage> logMessageList) {
    if (!CollectionUtils.isEmpty(logMessageList)) {
      elasticSearchClient.save(logMessageList);
    }
  }
}
