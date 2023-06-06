package com.epam.reportportal.analyzer.index;

import static com.epam.reportportal.analyzer.RabbitMqManagementClientTemplate.EXCHANGE_PRIORITY;

import com.epam.reportportal.analyzer.RabbitMqManagementClient;
import com.epam.reportportal.model.index.CleanIndexByDateRangeRq;
import com.epam.reportportal.model.index.CleanIndexRq;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
@Service
public class IndexerServiceClientImpl implements IndexerServiceClient {

  private static final String CLEAN_ROUTE = "clean";
  private static final String CLEAN_BY_LOG_DATE_ROUTE = "remove_by_log_time";
  private static final String CLEAN_BY_LAUNCH_DATE_ROUTE = "remove_by_launch_start_time";
  private static final String EXCHANGE_NAME = "analyzer-default";
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

  private void sendRangeRemovingMessageToRoute(Long index, LocalDateTime lessThanDate,
      String route) {
    CleanIndexByDateRangeRq message = new CleanIndexByDateRangeRq(index, OLDEST_DATE, lessThanDate);
    rabbitTemplate.convertAndSend(EXCHANGE_NAME, route, message);
  }

}
