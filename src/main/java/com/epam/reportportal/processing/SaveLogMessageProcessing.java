package com.epam.reportportal.processing;

import com.epam.reportportal.calculation.BatchProcessing;
import com.epam.reportportal.elastic.dao.LogMessageRepository;
import com.epam.reportportal.log.LogMessage;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.CollectionUtils;

import java.util.List;

public class SaveLogMessageProcessing extends BatchProcessing<LogMessage> {
    private final LogMessageRepository logMessageRepository;

    public SaveLogMessageProcessing(LogMessageRepository logMessageRepository, int batchSize, long timeout,
                             TaskScheduler scheduler) {
        super(batchSize, timeout, scheduler);
        this.logMessageRepository = logMessageRepository;
    }

    @Override
    protected void process(List<LogMessage> logMessageList) {
        if (!CollectionUtils.isEmpty(logMessageList)) {
            logMessageList.forEach(logMessage -> logMessageRepository.save(logMessage));
        }
    }
}