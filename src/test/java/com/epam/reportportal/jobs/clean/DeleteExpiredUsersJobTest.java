package com.epam.reportportal.jobs.clean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.epam.reportportal.analyzer.index.IndexerServiceClient;
import com.epam.reportportal.model.EmailNotificationRequest;
import com.epam.reportportal.model.event.domain.UsersDeletedEvent;
import com.epam.reportportal.service.MessageBus;
import com.epam.reportportal.storage.DataStorageService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DeleteExpiredUsersJobTest {

  @Mock
  private JdbcTemplate jdbcTemplate;

  @Mock
  private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @Mock
  private DataStorageService dataStorageService;

  @Mock
  private IndexerServiceClient indexerServiceClient;

  @Mock
  private MessageBus messageBus;

  @InjectMocks
  private DeleteExpiredUsersJob deleteExpiredUsersJob;

  @Captor
  private ArgumentCaptor<UsersDeletedEvent> usersDeletedEventCaptor;

  @Captor
  private ArgumentCaptor<List<EmailNotificationRequest>> emailCaptor;

  @BeforeEach
  void setUp() {
    setRetentionPeriod(30L);
  }

  @Test
  void executeWhenExpiredUsersFoundShouldDeleteUsersAndPublishEvents() throws Exception {
    // Given
    DeleteExpiredUsersJob.User user1 = createUser(1L, "user1@test.com");
    DeleteExpiredUsersJob.User user2 = createUser(2L, "user2@test.com");
    List<DeleteExpiredUsersJob.User> expiredUsers = List.of(user1, user2);
    List<Long> personalProjectIds = List.of(10L, 20L);
    List<String> userAttachments = List.of("attachment1", "attachment2");

    when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class),
        any(RowMapper.class)))
        .thenReturn(expiredUsers);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class),
        eq(Long.class)))
        .thenReturn(personalProjectIds);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class),
        eq(String.class)))
        .thenReturn(userAttachments);
    when(namedParameterJdbcTemplate.queryForObject(anyString(), isA(MapSqlParameterSource.class),
        eq(Boolean.class)))
        .thenReturn(true);

    // When
    deleteExpiredUsersJob.execute();

    // Then
    assertNotNull(deleteExpiredUsersJob);

    verify(namedParameterJdbcTemplate, times(1))
        .query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    verify(namedParameterJdbcTemplate, times(1))
        .queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    verify(namedParameterJdbcTemplate, times(1))
        .queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class));

    verify(dataStorageService, times(1)).deleteAll(anyList());
    verify(indexerServiceClient, times(2)).removeSuggest(anyLong());
    verify(indexerServiceClient, times(2)).deleteIndex(anyLong());
    verify(messageBus, times(1)).publishDomainEvent(usersDeletedEventCaptor.capture());

    UsersDeletedEvent publishedEvent = usersDeletedEventCaptor.getValue();
    assertNotNull(publishedEvent);
    assertEquals(2, publishedEvent.getCount());
    assertTrue(publishedEvent.isSystemEvent());

    verify(messageBus, times(1)).publishEmailNotificationEvents(emailCaptor.capture());
    List<List<EmailNotificationRequest>> emailNotifications = emailCaptor.getAllValues();
    assertEquals(1, emailNotifications.size());
    assertEquals(2, emailNotifications.getFirst().size());
  }

  @Test
  void executeWhenNoExpiredUsersFoundShouldNotDeleteAnything() throws Exception {
    // Given
    List<DeleteExpiredUsersJob.User> emptyUsers = Collections.emptyList();
    when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class),
        any(RowMapper.class)))
        .thenReturn(emptyUsers);

    // When
    deleteExpiredUsersJob.execute();

    // Then
    assertNotNull(deleteExpiredUsersJob);
    assertTrue(emptyUsers.isEmpty());

    verify(namedParameterJdbcTemplate, times(1))
        .query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));

    verify(dataStorageService, never()).deleteAll(anyList());
    verify(indexerServiceClient, never()).removeSuggest(anyLong());
    verify(indexerServiceClient, never()).deleteIndex(anyLong());
    verify(namedParameterJdbcTemplate, never()).update(anyString(),
        any(MapSqlParameterSource.class));
    verify(messageBus, never()).publishDomainEvent(any());
    verify(messageBus, times(1)).publishEmailNotificationEvents(Collections.emptyList());
  }

  @Test
  void executeWhenInvalidRetentionPeriodShouldNotDeleteUsers() throws Exception {
    // Given
    setRetentionPeriod(-1L);

    // When
    deleteExpiredUsersJob.execute();

    // Then
    assertNotNull(deleteExpiredUsersJob);
    verify(namedParameterJdbcTemplate, never())
        .query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    verify(dataStorageService, never()).deleteAll(anyList());
    verify(indexerServiceClient, never()).removeSuggest(anyLong());
    verify(indexerServiceClient, never()).deleteIndex(anyLong());
    verify(namedParameterJdbcTemplate, never()).update(anyString(),
        any(MapSqlParameterSource.class));

    verify(messageBus, never()).publishDomainEvent(any());
    verify(messageBus, never()).publishEmailNotificationEvents(anyList());
  }

  @Test
  void executeWhenDataStorageServiceThrowsExceptionShouldContinueExecution() throws Exception {
    // Given
    DeleteExpiredUsersJob.User user = createUser(1L, "user@test.com");
    List<DeleteExpiredUsersJob.User> expiredUsers = List.of(user);
    List<Long> personalProjectIds = List.of(10L);
    List<String> userAttachments = List.of("attachment1");

    when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class),
        any(RowMapper.class)))
        .thenReturn(expiredUsers);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class),
        eq(Long.class)))
        .thenReturn(personalProjectIds);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class),
        eq(String.class)))
        .thenReturn(userAttachments);
    when(namedParameterJdbcTemplate.queryForObject(anyString(), isA(MapSqlParameterSource.class),
        eq(Boolean.class)))
        .thenReturn(true);
    doThrow(new RuntimeException("Storage error")).when(dataStorageService).deleteAll(anyList());

    // When
    deleteExpiredUsersJob.execute();

    // Then
    assertNotNull(deleteExpiredUsersJob);
    assertEquals(1, expiredUsers.size());
    assertEquals(1, personalProjectIds.size());

    verify(dataStorageService, times(1)).deleteAll(anyList());
    verify(indexerServiceClient, times(1)).removeSuggest(anyLong());
    verify(indexerServiceClient, times(1)).deleteIndex(anyLong());
    verify(namedParameterJdbcTemplate, atLeast(1)).update(anyString(),
        any(MapSqlParameterSource.class));
    verify(messageBus, times(1)).publishDomainEvent(any(UsersDeletedEvent.class));
    verify(messageBus, times(1)).publishEmailNotificationEvents(anyList());
  }

  @Test
  void executeWhenUsersHaveNoPersonalProjectsShouldOnlyDeleteUsers() throws Exception {
    // Given
    DeleteExpiredUsersJob.User user = createUser(1L, "user@test.com");
    List<DeleteExpiredUsersJob.User> expiredUsers = List.of(user);
    List<Long> personalProjectIds = Collections.emptyList();
    List<String> userAttachments = List.of("attachment1");

    when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class),
        any(RowMapper.class)))
        .thenReturn(expiredUsers);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class),
        eq(Long.class)))
        .thenReturn(personalProjectIds);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class),
        eq(String.class)))
        .thenReturn(userAttachments);

    // When
    deleteExpiredUsersJob.execute();

    // Then
    assertNotNull(deleteExpiredUsersJob);
    assertEquals(1, expiredUsers.size());

    verify(dataStorageService, times(1)).deleteAll(anyList());
    verify(indexerServiceClient, never()).removeSuggest(anyLong());
    verify(indexerServiceClient, never()).deleteIndex(anyLong());
    verify(namedParameterJdbcTemplate, atLeast(1)).update(anyString(),
        any(MapSqlParameterSource.class));
    verify(messageBus, times(1)).publishDomainEvent(usersDeletedEventCaptor.capture());

    UsersDeletedEvent publishedEvent = usersDeletedEventCaptor.getValue();
    assertNotNull(publishedEvent);
    assertEquals(1, publishedEvent.getCount());
    assertTrue(publishedEvent.isSystemEvent());

    verify(messageBus, times(1)).publishEmailNotificationEvents(emailCaptor.capture());
    List<List<EmailNotificationRequest>> emailNotifications = emailCaptor.getAllValues();
    assertEquals(1, emailNotifications.size());
    assertEquals(1, emailNotifications.getFirst().size());
  }

  @Test
  void executeWhenProjectsHaveNoLaunchesShouldNotCallIndexerService() throws Exception {
    // Given
    DeleteExpiredUsersJob.User user = createUser(1L, "user@test.com");
    List<DeleteExpiredUsersJob.User> expiredUsers = List.of(user);
    List<Long> personalProjectIds = List.of(10L, 20L);
    List<String> userAttachments = List.of("attachment1");

    when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class),
        any(RowMapper.class)))
        .thenReturn(expiredUsers);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class),
        eq(Long.class)))
        .thenReturn(personalProjectIds);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class),
        eq(String.class)))
        .thenReturn(userAttachments);
    when(namedParameterJdbcTemplate.queryForObject(anyString(), isA(MapSqlParameterSource.class),
        eq(Boolean.class)))
        .thenReturn(false);

    // When
    deleteExpiredUsersJob.execute();

    // Then
    assertNotNull(deleteExpiredUsersJob);
    assertEquals(1, expiredUsers.size());
    assertEquals(2, personalProjectIds.size());

    verify(dataStorageService, times(1)).deleteAll(anyList());
    verify(indexerServiceClient, never()).removeSuggest(anyLong());
    verify(indexerServiceClient, never()).deleteIndex(anyLong());
    verify(namedParameterJdbcTemplate, atLeast(1)).update(anyString(),
        any(MapSqlParameterSource.class));
    verify(messageBus, times(1)).publishDomainEvent(any(UsersDeletedEvent.class));
    verify(messageBus, times(1)).publishEmailNotificationEvents(anyList());
  }

  @Test
  void executeShouldPublishCorrectDeletionCounts() {
    // Given
    DeleteExpiredUsersJob.User user1 = createUser(1L, "user1@test.com");
    DeleteExpiredUsersJob.User user2 = createUser(2L, "user2@test.com");
    DeleteExpiredUsersJob.User user3 = createUser(3L, "user3@test.com");
    List<DeleteExpiredUsersJob.User> expiredUsers = List.of(user1, user2, user3);
    List<Long> personalProjectIds = List.of(10L, 20L, 30L, 40L);
    List<String> userAttachments = List.of("attachment1", "attachment2", "attachment3");

    when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class),
        any(RowMapper.class)))
        .thenReturn(expiredUsers);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class),
        eq(Long.class)))
        .thenReturn(personalProjectIds);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class),
        eq(String.class)))
        .thenReturn(userAttachments);
    when(namedParameterJdbcTemplate.queryForObject(anyString(), isA(MapSqlParameterSource.class),
        eq(Boolean.class)))
        .thenReturn(true);

    // When
    deleteExpiredUsersJob.execute();

    // Then
    verify(namedParameterJdbcTemplate, atLeast(1)).update(anyString(),
        any(MapSqlParameterSource.class));
    verify(messageBus, times(1)).publishDomainEvent(usersDeletedEventCaptor.capture());

    UsersDeletedEvent publishedEvent = usersDeletedEventCaptor.getValue();
    assertNotNull(publishedEvent);
    assertEquals(3, publishedEvent.getCount());
    assertTrue(publishedEvent.isSystemEvent());

    verify(messageBus, times(1)).publishEmailNotificationEvents(emailCaptor.capture());
    List<List<EmailNotificationRequest>> emailNotifications = emailCaptor.getAllValues();
    assertEquals(1, emailNotifications.size());
    assertEquals(3, emailNotifications.getFirst().size());

    verify(indexerServiceClient, times(4)).removeSuggest(anyLong());
    verify(indexerServiceClient, times(4)).deleteIndex(anyLong());
  }

  private DeleteExpiredUsersJob.User createUser(Long userId, String email) {
    DeleteExpiredUsersJob.User user = new DeleteExpiredUsersJob.User();
    user.setUserId(userId);
    user.setEmail(email);
    return user;
  }

  private void setRetentionPeriod(Long retentionPeriod) {
    try {
      var field = DeleteExpiredUsersJob.class.getDeclaredField("retentionPeriod");
      field.setAccessible(true);
      field.set(deleteExpiredUsersJob, retentionPeriod);
      assertEquals(retentionPeriod, getRetentionPeriod());
    } catch (Exception e) {
      throw new RuntimeException("Failed to set retention period", e);
    }
  }

  private Long getRetentionPeriod() {
    try {
      var field = DeleteExpiredUsersJob.class.getDeclaredField("retentionPeriod");
      field.setAccessible(true);
      return (Long) field.get(deleteExpiredUsersJob);
    } catch (Exception e) {
      throw new RuntimeException("Failed to get retention period", e);
    }
  }
}
