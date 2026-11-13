/*
 * Copyright 2023 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.epam.reportportal.service.impl;

import static com.epam.reportportal.config.rabbit.InternalConfiguration.EXCHANGE_NOTIFICATION;
import static com.epam.reportportal.config.rabbit.InternalConfiguration.QUEUE_EMAIL;

import com.epam.reportportal.model.EmailNotificationRequest;
import com.epam.reportportal.model.event.domain.AbstractEvent;
import com.epam.reportportal.service.MessageBus;
import java.util.List;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * MessageBus implementation using RabbitMQ for transfer message.
 *
 * @author Ryhor_Kukharenka
 */
@Service
public class MessageBusImpl implements MessageBus {

  private static final String DOMAIN_EVENTS_EXCHANGE = "domain.events";

  private final RabbitTemplate rabbitTemplate;

  public MessageBusImpl(@Qualifier("rabbitTemplate") RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  /**
   * Publishes domain event to RabbitMQ. Events are published as-is (no conversion) to the
   * domain.events exchange.
   *
   * @param event The domain event to publish
   */
  @Override
  public void publishDomainEvent(AbstractEvent<?> event) {
    String routingKey = String.format("domain.%s", event.getClass().getSimpleName());
    rabbitTemplate.convertAndSend(DOMAIN_EVENTS_EXCHANGE, routingKey, event);
  }

  @Override
  public void publishEmailNotificationEvents(List<EmailNotificationRequest> notifications) {
    notifications.forEach(notification ->
        rabbitTemplate.convertAndSend(EXCHANGE_NOTIFICATION, QUEUE_EMAIL, notification));
  }

}
