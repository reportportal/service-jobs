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
import com.epam.reportportal.model.activity.Activity;
import com.epam.reportportal.model.activity.ActivityEvent;
import com.epam.reportportal.service.MessageBus;
import java.util.List;
import java.util.Objects;
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

  public static final String USER_DELETION_TEMPLATE = "userDeletionNotification";

  private final RabbitTemplate rabbitTemplate;

  public MessageBusImpl(@Qualifier("rabbitTemplate") RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  /**
   * Publishes activity to the queue with the following routing key.
   *
   * @param event Activity event to be converted to Activity object
   * @author Ryhor_Kuharenka
   */
  @Override
  public void publishActivity(ActivityEvent event) {
    final Activity activity = event.toActivity();
    if (Objects.nonNull(activity)) {
      rabbitTemplate.convertAndSend("activity", generateKeyForActivity(activity), activity);
    }
  }

  private String generateKeyForActivity(Activity activity) {
    return String.format("activity.%d.%s.%s",
        activity.getProjectId(),
        activity.getObjectType(),
        activity.getEventName());
  }

  @Override
  public void sendNotificationEmail(List<String> recipients) {
    for (String recipient : recipients) {
      EmailNotificationRequest notification =
          new EmailNotificationRequest(recipient, USER_DELETION_TEMPLATE);
      rabbitTemplate.convertAndSend(EXCHANGE_NOTIFICATION, QUEUE_EMAIL, notification);
    }
  }
}
