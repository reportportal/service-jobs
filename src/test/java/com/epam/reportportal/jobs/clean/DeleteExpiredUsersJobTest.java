package com.epam.reportportal.jobs.clean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.epam.reportportal.analyzer.index.IndexerServiceClient;
import com.epam.reportportal.model.EmailNotificationRequest;
import com.epam.reportportal.model.activity.ActivityEvent;
import com.epam.reportportal.model.activity.event.ProjectDeletedEvent;
import com.epam.reportportal.model.activity.event.UnassignUserEvent;
import com.epam.reportportal.model.activity.event.UserDeletedEvent;
import com.epam.reportportal.service.MessageBus;
import com.epam.reportportal.storage.DataStorageService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DeleteExpiredUsersJobTest {

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
  private ArgumentCaptor<ActivityEvent> activityCaptor;

  @Captor
  private ArgumentCaptor<List<EmailNotificationRequest>> emailCaptor;

  @BeforeEach
  void setUp() {
    setRetentionPeriod(30L);
  }

  @Test
  void execute_WhenExpiredUsersFound_ShouldDeleteUsersAndPublishEvents() throws Exception {
    // Given
    DeleteExpiredUsersJob.User user1 = createUser(1L, "user1@test.com");
    DeleteExpiredUsersJob.User user2 = createUser(2L, "user2@test.com");
    List<DeleteExpiredUsersJob.User> expiredUsers = List.of(user1, user2);
    List<Long> personalProjectIds = List.of(10L, 20L);
    List<DeleteExpiredUsersJob.ProjectOrganization> nonPersonalProjects = List.of(
        createProjectOrganization(100L, 1L),
        createProjectOrganization(200L, 2L)
    );
    List<String> userAttachments = List.of("attachment1", "attachment2");

    when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(expiredUsers);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(personalProjectIds);
    when(namedParameterJdbcTemplate.query(anyString(), any(Map.class), any(RowMapper.class)))
        .thenReturn(nonPersonalProjects);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
        .thenReturn(userAttachments);

    // When
    deleteExpiredUsersJob.execute();

    // Then
    assertNotNull(deleteExpiredUsersJob);

    verify(namedParameterJdbcTemplate, times(1))
        .query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    verify(namedParameterJdbcTemplate, times(1))
        .queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    verify(namedParameterJdbcTemplate, times(1))
        .query(anyString(), any(Map.class), any(RowMapper.class));
    verify(namedParameterJdbcTemplate, times(1))
        .queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class));

    verify(dataStorageService, times(1)).deleteAll(anyList());
    verify(indexerServiceClient, times(2)).removeSuggest(anyLong());
    verify(indexerServiceClient, times(2)).deleteIndex(anyLong());
    verify(messageBus, times(4)).publishActivity(activityCaptor.capture());

    List<ActivityEvent> publishedActivities = activityCaptor.getAllValues();

    long projectDeletedEvents = publishedActivities.stream()
        .filter(activity -> activity instanceof ProjectDeletedEvent)
        .count();
    assertEquals(1, projectDeletedEvents);

    long userDeletedEvents = publishedActivities.stream()
        .filter(activity -> activity instanceof UserDeletedEvent)
        .count();
    assertEquals(1, userDeletedEvents);

    long unassignUserEvents = publishedActivities.stream()
        .filter(activity -> activity instanceof UnassignUserEvent)
        .count();
    assertEquals(2, unassignUserEvents);

    verify(messageBus, times(1)).publishEmailNotificationEvents(emailCaptor.capture());
    List<List<EmailNotificationRequest>> emailNotifications = emailCaptor.getAllValues();
    assertEquals(1, emailNotifications.size());
    assertEquals(2, emailNotifications.getFirst().size());
  }

  @Test
  void execute_WhenNoExpiredUsersFound_ShouldNotDeleteAnything() throws Exception {
    // Given
    List<DeleteExpiredUsersJob.User> emptyUsers = Collections.emptyList();
    when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
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
    verify(namedParameterJdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    verify(messageBus, times(1)).publishActivity(any());
    verify(messageBus, times(1)).publishEmailNotificationEvents(anyList());
  }

  @Test
  void execute_WhenInvalidRetentionPeriod_ShouldNotDeleteUsers() throws Exception {
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
    verify(namedParameterJdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));

    verify(messageBus, never()).publishActivity(any());
    verify(messageBus, never()).publishEmailNotificationEvents(anyList());
  }

  @Test
  void execute_WhenDataStorageServiceThrowsException_ShouldContinueExecution() throws Exception {
    // Given
    DeleteExpiredUsersJob.User user = createUser(1L, "user@test.com");
    List<DeleteExpiredUsersJob.User> expiredUsers = List.of(user);
    List<Long> personalProjectIds = List.of(10L);
    List<DeleteExpiredUsersJob.ProjectOrganization> nonPersonalProjects = Collections.emptyList();
    List<String> userAttachments = List.of("attachment1");

    when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(expiredUsers);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(personalProjectIds);
    when(namedParameterJdbcTemplate.query(anyString(), any(Map.class), any(RowMapper.class)))
        .thenReturn(nonPersonalProjects);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
        .thenReturn(userAttachments);
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
    verify(messageBus, times(2)).publishActivity(any());
    verify(messageBus, times(1)).publishEmailNotificationEvents(anyList());
  }

  @Test
  void execute_WhenUsersHaveNoPersonalProjects_ShouldOnlyDeleteUsers() throws Exception {
    // Given
    DeleteExpiredUsersJob.User user = createUser(1L, "user@test.com");
    List<DeleteExpiredUsersJob.User> expiredUsers = List.of(user);
    List<Long> personalProjectIds = Collections.emptyList();
    List<DeleteExpiredUsersJob.ProjectOrganization> nonPersonalProjects = List.of(
        createProjectOrganization(100L, 1L)
    );
    List<String> userAttachments = List.of("attachment1");

    when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(expiredUsers);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(personalProjectIds);
    when(namedParameterJdbcTemplate.query(anyString(), any(Map.class), any(RowMapper.class)))
        .thenReturn(nonPersonalProjects);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
        .thenReturn(userAttachments);

    // When
    deleteExpiredUsersJob.execute();

    // Then
    assertNotNull(deleteExpiredUsersJob);
    assertEquals(1, expiredUsers.size());
    assertEquals(1, nonPersonalProjects.size());

    verify(dataStorageService, times(1)).deleteAll(anyList());
    verify(indexerServiceClient, never()).removeSuggest(anyLong());
    verify(indexerServiceClient, never()).deleteIndex(anyLong());
    verify(messageBus, times(3)).publishActivity(activityCaptor.capture());

    List<ActivityEvent> publishedActivities = activityCaptor.getAllValues();

    long projectDeletedEvents = publishedActivities.stream()
        .filter(activity -> activity instanceof ProjectDeletedEvent)
        .count();
    assertEquals(1, projectDeletedEvents);

    long userDeletedEvents = publishedActivities.stream()
        .filter(activity -> activity instanceof UserDeletedEvent)
        .count();
    assertEquals(1, userDeletedEvents);

    long unassignUserEvents = publishedActivities.stream()
        .filter(activity -> activity instanceof UnassignUserEvent)
        .count();
    assertEquals(1, unassignUserEvents);

    verify(messageBus, times(1)).publishEmailNotificationEvents(emailCaptor.capture());
    List<List<EmailNotificationRequest>> emailNotifications = emailCaptor.getAllValues();
    assertEquals(1, emailNotifications.size());
    assertEquals(1, emailNotifications.getFirst().size());
  }

  @Test
  void execute_ShouldPublishCorrectDeletionCounts() {
    // Given
    DeleteExpiredUsersJob.User user1 = createUser(1L, "user1@test.com");
    DeleteExpiredUsersJob.User user2 = createUser(2L, "user2@test.com");
    DeleteExpiredUsersJob.User user3 = createUser(3L, "user3@test.com");
    List<DeleteExpiredUsersJob.User> expiredUsers = List.of(user1, user2, user3);
    List<Long> personalProjectIds = List.of(10L, 20L, 30L, 40L);
    List<DeleteExpiredUsersJob.ProjectOrganization> nonPersonalProjects = List.of(
        createProjectOrganization(100L, 1L),
        createProjectOrganization(200L, 2L)
    );
    List<String> userAttachments = List.of("attachment1", "attachment2", "attachment3");

    when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(expiredUsers);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(personalProjectIds);
    when(namedParameterJdbcTemplate.query(anyString(), any(Map.class), any(RowMapper.class)))
        .thenReturn(nonPersonalProjects);
    when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
        .thenReturn(userAttachments);

    // When
    deleteExpiredUsersJob.execute();

    // Then
    verify(messageBus, times(4)).publishActivity(activityCaptor.capture());

    List<ActivityEvent> publishedActivities = activityCaptor.getAllValues();

    long projectDeletedEvents = publishedActivities.stream()
        .filter(activity -> activity instanceof ProjectDeletedEvent)
        .count();
    assertEquals(1, projectDeletedEvents);

    long userDeletedEvents = publishedActivities.stream()
        .filter(activity -> activity instanceof UserDeletedEvent)
        .count();
    assertEquals(1, userDeletedEvents);

    long unassignUserEvents = publishedActivities.stream()
        .filter(activity -> activity instanceof UnassignUserEvent)
        .count();
    assertEquals(2, unassignUserEvents);

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

  private DeleteExpiredUsersJob.ProjectOrganization createProjectOrganization(Long projectId, Long organizationId) {
    DeleteExpiredUsersJob.ProjectOrganization projectOrg = new DeleteExpiredUsersJob.ProjectOrganization();
    projectOrg.setProjectId(projectId);
    projectOrg.setOrganizationId(organizationId);
    return projectOrg;
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
