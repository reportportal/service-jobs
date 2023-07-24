package com.epam.reportportal.jobs.clean;

import com.epam.reportportal.jobs.BaseJob;
import com.epam.reportportal.model.BlobNotFoundException;
import com.epam.reportportal.storage.DataStorageService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removing data from storage.
 *
 * @author <a href="mailto:maksim_antonov@epam.com">Maksim Antonov</a>
 */
@Service
public class CleanStorageJob extends BaseJob {

  private static final String ROLLBACK_ERROR_MESSAGE = "Rollback deleting transaction.";
  private static final String SELECT_AND_DELETE_DATA_CHUNK_QUERY =
      "DELETE FROM attachment_deletion WHERE id IN "
          + "(SELECT id FROM attachment_deletion ORDER BY id LIMIT ?) RETURNING *";

  private static final int MAX_BATCH_SIZE = 200000;
  private final DataStorageService storageService;
  private final int chunkSize;

  private final int batchSize;

  /**
   * Initializes {@link CleanStorageJob}.
   *
   * @param jdbcTemplate   {@link JdbcTemplate}
   * @param storageService {@link DataStorageService}
   * @param chunkSize      Size of elements deleted at once
   */
  public CleanStorageJob(JdbcTemplate jdbcTemplate, DataStorageService storageService,
      @Value("${rp.environment.variable.clean.storage.chunkSize}") int chunkSize) {
    super(jdbcTemplate);
    this.chunkSize = chunkSize;
    this.storageService = storageService;
    this.batchSize = chunkSize <= MAX_BATCH_SIZE ? chunkSize : MAX_BATCH_SIZE;
  }

  /**
   * Deletes attachments, which are set to be deleted.
   */
  @Scheduled(cron = "${rp.environment.variable.clean.storage.cron}")
  @SchedulerLock(name = "cleanStorage", lockAtMostFor = "24h")
  @Transactional
  public void execute() {
    AtomicInteger counter = new AtomicInteger(0);

    int batchNumber = 1;
    while (batchNumber * batchSize <= chunkSize) {
      List<String> attachments = new ArrayList<>();
      List<String> thumbnails = new ArrayList<>();
      jdbcTemplate.query(SELECT_AND_DELETE_DATA_CHUNK_QUERY, rs -> {
        do {
          String attachment = rs.getString("file_id");
          String thumbnail = rs.getString("thumbnail_id");
          if (attachment != null) {
            attachments.add(attachment);
          }
          if (thumbnail != null) {
            thumbnails.add(thumbnail);
          }
        } while (rs.next());
      }, batchSize);

      int attachmentsSize = thumbnails.size() + attachments.size();
      if (attachmentsSize == 0) {
        break;
      }
      try {
        storageService.deleteAll(
            thumbnails.stream().map(this::decode).collect(Collectors.toList()));
        storageService.deleteAll(
            attachments.stream().map(this::decode).collect(Collectors.toList()));
      } catch (BlobNotFoundException e) {
        LOGGER.info("File is not found when executing clean storage job");
      } catch (Exception e) {
        throw new RuntimeException(ROLLBACK_ERROR_MESSAGE, e);
      }

      counter.addAndGet(attachmentsSize);
      LOGGER.info("Iteration {}, deleted {} attachments", batchNumber, attachmentsSize);
      batchNumber++;
    }
  }

  private String decode(String data) {
    return StringUtils.isEmpty(data) ? data :
        new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
  }
}
