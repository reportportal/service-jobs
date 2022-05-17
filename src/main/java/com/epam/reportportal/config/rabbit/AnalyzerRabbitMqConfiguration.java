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

import com.epam.reportportal.analyzer.RabbitMqManagementClient;
import com.epam.reportportal.analyzer.RabbitMqManagementClientTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.http.client.Client;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@EnableRabbit
@Configuration
public class AnalyzerRabbitMqConfiguration {

	private final ObjectMapper objectMapper;

	@Autowired
	public AnalyzerRabbitMqConfiguration(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Bean
	public MessageConverter jsonMessageConverter() {
		return new Jackson2JsonMessageConverter(objectMapper);
	}

	@Bean
	public RabbitMqManagementClient managementTemplate(@Value("${rp.amqp.api-address}") String address,
			@Value("${rp.amqp.analyzer-vhost}") String virtualHost)
			throws MalformedURLException, URISyntaxException, JsonProcessingException {
		final Client rabbitClient = new Client(address);
		return new RabbitMqManagementClientTemplate(rabbitClient, virtualHost);
	}

	@Bean(name = "analyzerConnectionFactory")
	public ConnectionFactory analyzerConnectionFactory(@Value("${rp.amqp.addresses}") URI addresses,
			@Value("${rp.amqp.analyzer-vhost}") String virtualHost) {
		CachingConnectionFactory factory = new CachingConnectionFactory(addresses);
		factory.setVirtualHost(virtualHost);
		return factory;
	}

	@Bean(name = "analyzerRabbitTemplate")
	public RabbitTemplate analyzerRabbitTemplate(@Autowired @Qualifier("analyzerConnectionFactory") ConnectionFactory connectionFactory,
			@Value("${rp.amqp.reply-timeout}") long replyTimeout) {
		RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
		rabbitTemplate.setMessageConverter(jsonMessageConverter());
		rabbitTemplate.setReplyTimeout(replyTimeout);
		return rabbitTemplate;
	}

}
