package com.epam.reportportal.jobs.processing;

import com.epam.reportportal.log.LogMessage;
import com.epam.reportportal.log.LogProcessing;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Log consumer.
 *
 * @author <a href="mailto:maksim_antonov@epam.com">Maksim Antonov</a>
 */
@Service
public class SaveLogMessageJob {
    public static final String LOG_MESSAGE_SAVING_QUEUE_NAME = "log_message_saving";
    private final LogProcessing logProcessing;

    public SaveLogMessageJob(LogProcessing logProcessing) {
        this.logProcessing = logProcessing;
    }

    @RabbitListener(queues = LOG_MESSAGE_SAVING_QUEUE_NAME, containerFactory = "processingRabbitListenerContainerFactory")
    public void execute(@Payload LogMessage logMessage) {
        if (Objects.nonNull(logMessage)) {
            this.logProcessing.add(logMessage);
        }
    }
}
