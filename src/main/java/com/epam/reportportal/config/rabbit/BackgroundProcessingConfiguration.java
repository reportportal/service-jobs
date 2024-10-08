package com.epam.reportportal.config.rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rabbitmq background queue configuration.
 *
 * @author <a href="mailto:maksim_antonov@epam.com">Maksim Antonov</a>
 */
@Configuration
@ConditionalOnProperty(prefix = "rp.searchengine", name = "host")
public class BackgroundProcessingConfiguration {

  public static final String LOG_MESSAGE_SAVING_QUEUE_NAME = "log_message_saving";
  public static final String LOG_MESSAGE_SAVING_ROUTING_KEY = "log_message_saving";
  public static final String PROCESSING_EXCHANGE_NAME = "processing";

  @Bean
  Queue logMessageSavingQueue() {
    return new Queue(LOG_MESSAGE_SAVING_QUEUE_NAME);
  }

  @Bean
  DirectExchange exchangeProcessing() {
    return new DirectExchange(PROCESSING_EXCHANGE_NAME);
  }

  @Bean
  Binding bindingSavingLogs(@Qualifier("logMessageSavingQueue") Queue queue,
      @Qualifier("exchangeProcessing") DirectExchange exchange) {
    return BindingBuilder.bind(queue).to(exchange).with(LOG_MESSAGE_SAVING_ROUTING_KEY);
  }
}
