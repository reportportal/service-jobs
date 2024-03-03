package com.epam.reportportal.analyzer.index;

import static com.epam.reportportal.analyzer.RabbitMqManagementClientTemplate.EXCHANGE_PRIORITY;

import com.epam.reportportal.analyzer.RabbitMqManagementClient;
import com.epam.reportportal.model.index.CleanIndexByDateRangeRq;
import com.epam.reportportal.model.index.CleanIndexRq;
import com.rabbitmq.http.client.domain.ExchangeInfo;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.epam.reportportal.analyzer.AnalyzerUtils.DOES_SUPPORT_SUGGEST;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class IndexerServiceClientImpl implements IndexerServiceClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(IndexerServiceClient.class);
	private static final String CLEAN_ROUTE = "clean";
	private static final String CLEAN_BY_LOG_DATE_ROUTE = "remove_by_log_time";
	private static final String CLEAN_BY_LAUNCH_DATE_ROUTE = "remove_by_launch_start_time";
	private static final String EXCHANGE_NAME = "analyzer-default";
	private static final String REMOVE_SUGGEST_ROUTE = "remove_suggest_info";
	static final String DELETE_ROUTE = "delete";
	private static final Integer DELETE_INDEX_SUCCESS_CODE = 1;
	// need to be in line with analyzer API, better to fix api and remove it in future.
	private static final LocalDateTime OLDEST_DATE = LocalDateTime.now().minusYears(10L);

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

  @Override
  public void removeFromIndexLessThanLogDate(Long index, LocalDateTime lessThanDate) {
    sendRangeRemovingMessageToRoute(index, lessThanDate, CLEAN_BY_LOG_DATE_ROUTE);
  }

  @Override
  public void removeFromIndexLessThanLaunchDate(Long index, LocalDateTime lessThanDate) {
    sendRangeRemovingMessageToRoute(index, lessThanDate, CLEAN_BY_LAUNCH_DATE_ROUTE);
  }

	@Override
	public void deleteIndex(Long index) {
		rabbitMqManagementClient.getAnalyzerExchangesInfo()
				.stream()
				.map(exchange -> rabbitTemplate.convertSendAndReceiveAsType(exchange.getName(),
						DELETE_ROUTE,
						index,
						new ParameterizedTypeReference<Integer>() {
						}
				))
				.forEach(it -> {
					if (DELETE_INDEX_SUCCESS_CODE.equals(it)) {
						LOGGER.info("Successfully deleted index '{}'", index);
					} else {
						LOGGER.error("Error deleting index '{}'", index);
					}
				});
	}

	private void sendRangeRemovingMessageToRoute(Long index, LocalDateTime lessThanDate, String route) {
		CleanIndexByDateRangeRq message = new CleanIndexByDateRangeRq(index, OLDEST_DATE, lessThanDate);
		rabbitTemplate.convertAndSend(EXCHANGE_NAME, route, message);
	}

	@Override
	public void removeSuggest(Long projectId) {
		resolveExchangeName(DOES_SUPPORT_SUGGEST)
				.ifPresent(suggestExchange -> rabbitTemplate.convertAndSend(suggestExchange, REMOVE_SUGGEST_ROUTE, projectId));
	}

	private Optional<String> resolveExchangeName(Predicate<ExchangeInfo> supportCondition) {
		return rabbitMqManagementClient.getAnalyzerExchangesInfo()
				.stream()
				.filter(supportCondition)
				.min(Comparator.comparingInt(EXCHANGE_PRIORITY))
				.map(ExchangeInfo::getName);
	}

}
