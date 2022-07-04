package com.epam.reportportal.log;

import com.epam.reportportal.calculation.BatchProcessing;
import com.epam.reportportal.elastic.SimpleElasticSearchClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.DefaultManagedTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Batch processing for log.
 * @author <a href="mailto:maksim_antonov@epam.com">Maksim Antonov</a>
 */
@Component
public class LogProcessing extends BatchProcessing<LogMessage> {

    private final SimpleElasticSearchClient simpleElasticSearchClient;

    public LogProcessing(SimpleElasticSearchClient simpleElasticSearchClient,
                         @Value("${rp.processing.log.maxBatchSize}") int batchSize,
                         @Value("${rp.processing.log.maxBatchTimeout}") int timeout) {
        super(batchSize, timeout, new DefaultManagedTaskScheduler());
        this.simpleElasticSearchClient = simpleElasticSearchClient;
    }

    @Override
    protected void process(List<LogMessage> logMessageList) {
        if (!CollectionUtils.isEmpty(logMessageList)) {
            simpleElasticSearchClient.save(logMessageList);
        }
    }
}
