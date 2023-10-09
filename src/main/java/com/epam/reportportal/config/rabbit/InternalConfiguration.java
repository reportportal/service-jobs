/*
 * Copyright 2023 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.config.rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ queue and exchange configuration.
 *
 * @author Andrei Piankouski
 */
@Configuration
public class InternalConfiguration {

  /**
   * Exchanges.
   */
  public static final String EXCHANGE_NOTIFICATION = "notification";

  /**
   * Queues.
   */
  public static final String QUEUE_EMAIL = "notification.email";

  @Bean
  Queue emailNotificationQueue() {
    return new Queue(QUEUE_EMAIL);
  }

  @Bean
  DirectExchange notificationExchange() {
    return new DirectExchange(EXCHANGE_NOTIFICATION);
  }

  @Bean
  public Binding emailNotificationBinding() {
    return BindingBuilder.bind(emailNotificationQueue()).to(notificationExchange())
        .with(QUEUE_EMAIL);
  }

}
