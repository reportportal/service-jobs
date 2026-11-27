package com.epam.reportportal.jobs.clean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class CleanTmsAttachmentJobTest {

  @Mock
  private JdbcTemplate jdbcTemplate;
  @Mock
  private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  private CleanTmsAttachmentJob job;

  @BeforeEach
  void setUp() {
    job = spy(new CleanTmsAttachmentJob(jdbcTemplate, namedParameterJdbcTemplate, 100));
    lenient().doReturn(Map.of()).when(job).getProjectsWithAttribute(any());
  }

  @Test
  void executeWithNoOrphanExecutionsMovesNothing() {
    when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(100))).thenReturn(List.of());

    job.execute();

    verify(namedParameterJdbcTemplate, never()).update(anyString(), anyMap());
  }

  @Test
  void executeWithOrphanExecutionsMovesAttachmentsToDeletionTable() {
    when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(100)))
        .thenReturn(List.of(1L, 2L)).thenReturn(List.of());
    when(namedParameterJdbcTemplate.queryForList(anyString(), anyMap(), eq(Long.class)))
        .thenReturn(List.of(10L));

    job.execute();

    verify(namedParameterJdbcTemplate).update(contains("DELETE FROM tms_test_case_execution "),
        eq(Map.of("ids", List.of(1L, 2L))));
    verify(namedParameterJdbcTemplate).update(contains("INSERT INTO attachment_deletion"),
        eq(Map.of("ids", List.of(10L))));
  }

  @Test
  void executeWithoutAttachmentsDoesNotMoveAnything() {
    when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(100)))
        .thenReturn(List.of(1L)).thenReturn(List.of());
    when(namedParameterJdbcTemplate.queryForList(anyString(), anyMap(), eq(Long.class)))
        .thenReturn(List.of());

    job.execute();

    verify(namedParameterJdbcTemplate, never()).update(contains("INSERT INTO attachment_deletion"),
        anyMap());
  }

  @Test
  void executeMovesUnlinkedAttachmentsUntilNothingLeft() {
    when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(100))).thenReturn(List.of());
    lenient().when(jdbcTemplate.update(contains("SELECT t.id FROM tms_attachment t"), eq(100)))
        .thenReturn(100).thenReturn(7).thenReturn(0);

    job.execute();

    verify(jdbcTemplate, times(3)).update(contains("SELECT t.id FROM tms_attachment t"), eq(100));
  }

  @Test
  void executeMovesExpiredAttachmentsPerProject() {
    doReturn(Map.of(5L, Duration.ofDays(1))).when(job).getProjectsWithAttribute("job.keepScreenshots");
    when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(100))).thenReturn(List.of());

    job.execute();

    verify(jdbcTemplate).update(contains("INSERT INTO attachment_deletion"), eq(5L),
        any(LocalDateTime.class));
  }
}
