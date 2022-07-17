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

package com.epam.reportportal.analyzer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rabbitmq.http.client.Client;
import com.rabbitmq.http.client.domain.ExchangeInfo;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.List;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingInt;
import static java.util.Optional.ofNullable;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class RabbitMqManagementClientTemplate implements RabbitMqManagementClient {

	public static final String ANALYZER_KEY = "analyzer";
	static final String ANALYZER_PRIORITY = "analyzer_priority";
	private final String virtualHost;

	public static final ToIntFunction<ExchangeInfo> EXCHANGE_PRIORITY = it -> ofNullable(it.getArguments()
			.get(ANALYZER_PRIORITY)).map(val -> NumberUtils.toInt(val.toString(), Integer.MAX_VALUE)).orElse(Integer.MAX_VALUE);

	private final Client rabbitClient;

	public RabbitMqManagementClientTemplate(Client rabbitClient, String virtualHost) throws JsonProcessingException {
		this.rabbitClient = rabbitClient;
		this.virtualHost = virtualHost;
		rabbitClient.createVhost(virtualHost);
	}

	public List<ExchangeInfo> getAnalyzerExchangesInfo() {
		return ofNullable(rabbitClient.getExchanges(virtualHost)).map(client -> client.stream()
						.filter(it -> it.getArguments().get(ANALYZER_KEY) != null)
						.sorted(comparingInt(EXCHANGE_PRIORITY))
						.collect(Collectors.toList()))
				.orElseThrow(() -> new RuntimeException("Unable to resolve exchanges for key: " + ANALYZER_KEY));
	}
}
