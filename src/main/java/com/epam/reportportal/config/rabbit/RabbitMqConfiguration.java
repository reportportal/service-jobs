/*
 * Copyright 2019 EPAM Systems
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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@EnableRabbit
@Configuration
public class RabbitMqConfiguration {

  private final ObjectMapper objectMapper;

  @Autowired
  public RabbitMqConfiguration(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Bean(name = "connectionFactory")
  public ConnectionFactory connectionFactory(@Value("${rp.amqp.addresses}") URI addresses,
      @Value("${rp.amqp.base-vhost}") String virtualHost) {
    CachingConnectionFactory factory = new CachingConnectionFactory(addresses);
    factory.setVirtualHost(virtualHost);
    return factory;
  }

  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      @Qualifier("connectionFactory") ConnectionFactory connectionFactory,
      MessageConverter jsonMessageConverter,
      @Value("${rp.amqp.maxLogConsumer}") int maxLogConsumer) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMaxConcurrentConsumers(maxLogConsumer);
    factory.setMessageConverter(jsonMessageConverter);
    return factory;
  }

  @Bean
  public RabbitAdmin rabbitAdmin(
      @Qualifier("connectionFactory") ConnectionFactory connectionFactory) {
    return new RabbitAdmin(connectionFactory);
  }

  @Bean(name = "rabbitTemplate")
  public RabbitTemplate rabbitTemplate(
      @Autowired @Qualifier("connectionFactory") ConnectionFactory connectionFactory,
      @Value("${rp.amqp.reply-timeout}") long replyTimeout) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter(objectMapper));
    rabbitTemplate.setReplyTimeout(replyTimeout);
    return rabbitTemplate;
  }
}
