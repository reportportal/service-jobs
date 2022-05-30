package com.epam.reportportal.jobs.processing;

import com.epam.reportportal.elastic.SimpleElasticSearchClient;
import com.epam.reportportal.log.LogMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SaveLogMessageJob {
    public static final String LOG_MESSAGE_SAVING_QUEUE_NAME = "log_message_saving";
    private final SimpleElasticSearchClient simpleElasticSearchClient;


    public SaveLogMessageJob(SimpleElasticSearchClient simpleElasticSearchClient) {
        this.simpleElasticSearchClient = simpleElasticSearchClient;
    }

    @RabbitListener(queues = LOG_MESSAGE_SAVING_QUEUE_NAME, containerFactory = "processingRabbitListenerContainerFactory")
    public void execute(@Payload LogMessage logMessage) {
        if (Objects.nonNull(logMessage)) {
//            simpleElasticSearchClient.save(logMessage);
        }
    }
}
