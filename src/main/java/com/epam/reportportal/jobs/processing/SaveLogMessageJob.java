package com.epam.reportportal.jobs.processing;

import com.epam.reportportal.elastic.dao.LogMessageRepository;
import com.epam.reportportal.log.LogMessage;
import com.epam.reportportal.processing.SaveLogMessageProcessing;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.concurrent.DefaultManagedTaskScheduler;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SaveLogMessageJob {
    public static final String LOG_MESSAGE_SAVING_QUEUE_NAME = "log_message_saving";

//    private final SaveLogMessageProcessing saveLogMessageProcessing;
    private final LogMessageRepository logMessageRepository;

    public SaveLogMessageJob(LogMessageRepository logMessageRepository) {
        this.logMessageRepository = logMessageRepository;
//        saveLogMessageProcessing = new SaveLogMessageProcessing(logMessageRepository,100, 3000,
//                new DefaultManagedTaskScheduler());
    }

    @RabbitListener(queues = LOG_MESSAGE_SAVING_QUEUE_NAME, containerFactory = "processingRabbitListenerContainerFactory")
    public void execute(@Payload LogMessage logMessage) {
        if (Objects.nonNull(logMessage)) {
            logMessageRepository.save(logMessage);
//            saveLogMessageProcessing.add(logMessage);
        }
    }
}
