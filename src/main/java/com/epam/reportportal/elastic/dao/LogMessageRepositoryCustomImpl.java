package com.epam.reportportal.elastic.dao;

import com.epam.reportportal.log.LogMessage;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Implement rewritten methods to be able to use separated indexes for single entity based on their fields.
 *
 */
public class LogMessageRepositoryCustomImpl implements LogMessageRepositoryCustom<LogMessage> {

    private final ElasticsearchOperations operations;

    private final ConcurrentHashMap<String, IndexCoordinates> knownIndexCoordinates = new ConcurrentHashMap<>();

    @Nullable
    private Document mapping;

    public LogMessageRepositoryCustomImpl(ElasticsearchOperations operations) {
        this.operations = operations;
    }


    @Override
    public <S extends LogMessage> S save(S logMessage) {
        IndexCoordinates indexCoordinates = getIndexCoordinates(logMessage);
        return operations.save(logMessage, indexCoordinates);
    }

    @NonNull
    private <S extends LogMessage> IndexCoordinates getIndexCoordinates(S LogMessage) {
        String indexName = "log_message_store-" + LogMessage.getProjectId();
        return knownIndexCoordinates.computeIfAbsent(indexName, i -> {
                    IndexCoordinates indexCoordinates = IndexCoordinates.of(i);
                    IndexOperations indexOps = operations.indexOps(indexCoordinates);
                    if (!indexOps.exists()) {
                        indexOps.create();
                        if (mapping == null) {
                            mapping = indexOps.createMapping(LogMessage.class);
                        }
                        indexOps.putMapping(mapping);
                    }
                    return indexCoordinates;
                }
        );
    }
}
