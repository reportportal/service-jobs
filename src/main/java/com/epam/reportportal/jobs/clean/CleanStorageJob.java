package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.jobs.BaseJob;
import com.epam.reportportal.storage.DataStorageService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Removing data from storage.
 */
@Component
public class CleanStorageJob extends BaseJob {

    private static final String ROLLBACK_ERROR_MESSAGE = "Rollback deleting transaction.";
    private static final String SELECT_AND_DELETE_DATA_CHUNK_QUERY = "DELETE FROM attachment_deletion WHERE id in " +
            "(SELECT id FROM attachment_deletion ORDER BY id LIMIT ?) RETURNING *";
    private final DataStorageService storageService;
    private final int chunkSize;

    public CleanStorageJob(JdbcTemplate jdbcTemplate, DataStorageService storageService,
                           @Value("${rp.environment.variable.clean.storage.chunkSize}") int chunkSize) {
        super(jdbcTemplate);
        this.chunkSize = chunkSize;
        this.storageService = storageService;
    }

    @Scheduled(cron = "${rp.environment.variable.clean.storage.cron}")
    @SchedulerLock(name = "cleanStorage", lockAtMostFor = "24h")
    @Transactional
    public void execute() {
        logStart();
        AtomicInteger counter = new AtomicInteger(0);

        jdbcTemplate.query(SELECT_AND_DELETE_DATA_CHUNK_QUERY, rs -> {
            try {
                delete(rs.getString("file_id"), rs.getString("thumbnail_id"));
                counter.incrementAndGet();
                while (rs.next()) {
                    delete(rs.getString("file_id"), rs.getString("thumbnail_id"));
                    counter.incrementAndGet();
                }
            } catch (Exception e) {
                throw new RuntimeException(ROLLBACK_ERROR_MESSAGE, e);
            }
        }, chunkSize);

        logFinish(counter.get());
    }

    private void delete(String fileId, String thumbnailId) throws Exception {
        if (Strings.isNotBlank(fileId)) {
            storageService.delete(decode(fileId));
        }
        if (Strings.isNotBlank(thumbnailId)) {
            storageService.delete(decode(thumbnailId));
        }
    }

    private String decode(String data) {
        return StringUtils.isEmpty(data) ? data : new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
    }
}
