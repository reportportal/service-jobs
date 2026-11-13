package com.epam.reportportal.jobs.clean;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class CleanLaunchJobTest {

  @Test
  void shouldExcludeManualLaunchesFromRetentionCleanup() throws NoSuchFieldException,
      IllegalAccessException {
    Field queryField = CleanLaunchJob.class.getDeclaredField("SELECT_LAUNCH_ID_QUERY");
    queryField.setAccessible(true);

    String query = (String) queryField.get(null);

    assertTrue(query.contains("launch_type <> 'MANUAL'"),
        "Manual Launches must not be selected by retention cleanup");
  }
}