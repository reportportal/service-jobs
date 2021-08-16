package com.epam.reportportal.analyzer.index;

import com.epam.reportportal.analyzer.RabbitMqManagementClient;
import com.epam.reportportal.model.index.CleanIndexRq;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.epam.reportportal.analyzer.RabbitMqManagementClientTemplate.EXCHANGE_PRIORITY;

@Service
public class IndexerServiceClientImpl implements IndexerServiceClient {

	private static final String CLEAN_ROUTE = "clean";

	private final RabbitMqManagementClient rabbitMqManagementClient;
	private final RabbitTemplate rabbitTemplate;

	@Autowired
	public IndexerServiceClientImpl(RabbitMqManagementClient rabbitMqManagementClient,
			@Qualifier("analyzerRabbitTemplate") RabbitTemplate rabbitTemplate) {
		this.rabbitMqManagementClient = rabbitMqManagementClient;
		this.rabbitTemplate = rabbitTemplate;
	}

	@Override
	public Long cleanIndex(Long index, List<Long> ids) {
		final Map<Integer, Long> priorityToCleanedLogsCountMapping = rabbitMqManagementClient.getAnalyzerExchangesInfo()
				.stream()
				.collect(Collectors.toMap(EXCHANGE_PRIORITY::applyAsInt,
						exchange -> rabbitTemplate.convertSendAndReceiveAsType(exchange.getName(),
								CLEAN_ROUTE,
								new CleanIndexRq(index, ids),
								new ParameterizedTypeReference<>() {
								}
						)
				));
		return priorityToCleanedLogsCountMapping.entrySet()
				.stream()
				.min(Map.Entry.comparingByKey())
				.orElseGet(() -> new AbstractMap.SimpleEntry<>(0, 0L))
				.getValue();
	}

}
