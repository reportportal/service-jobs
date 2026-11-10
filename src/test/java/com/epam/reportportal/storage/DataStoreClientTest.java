package com.epam.reportportal.storage;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.epam.reportportal.utils.FeatureFlag;
import com.epam.reportportal.utils.FeatureFlagHandler;
import java.util.List;
import org.apache.opendal.Operator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataStoreClientTest {

  @Mock
  private Operator operator;

  @Mock
  private Operator projectOperator;

  @Mock
  private S3OperatorFactory operatorFactory;

  @Mock
  private FeatureFlagHandler featureFlagHandler;

  private DataStoreClient containerClient() {
    return new DataStoreClient(operator, "prj-", "", "rp-bucket", featureFlagHandler);
  }

  private DataStoreClient perBucketClient() {
    return new DataStoreClient(operatorFactory, "prj-", "", "rp-bucket", featureFlagHandler);
  }

  @Test
  void deleteAllWithEmptyPathsDoesNothing() {
    containerClient().deleteAll(List.of());

    verify(operator, never()).delete(anyString());
  }

  @Test
  void containerDeleteAllInSingleBucketModeUsesPathsAsIs() {
    when(featureFlagHandler.isEnabled(FeatureFlag.SINGLE_BUCKET)).thenReturn(true);

    containerClient().deleteAll(List.of("project-data/1/attachment", "user-data/photo"));

    verify(operator).delete("project-data/1/attachment");
    verify(operator).delete("user-data/photo");
  }

  @Test
  void containerDeleteAllInMultiBucketModePrefixesProjectBucketDirectory() {
    when(featureFlagHandler.isEnabled(FeatureFlag.SINGLE_BUCKET)).thenReturn(false);

    containerClient().deleteAll(List.of("42/2025-1/attachment", "users/photo"));

    verify(operator).delete("prj-42/2025-1/attachment");
    verify(operator).delete("prj-users/photo");
  }

  @Test
  void containerDeleteAllContinuesAfterFailedDelete() {
    when(featureFlagHandler.isEnabled(FeatureFlag.SINGLE_BUCKET)).thenReturn(true);
    doThrow(new RuntimeException("boom")).when(operator).delete("first");

    containerClient().deleteAll(List.of("first", "second"));

    verify(operator).delete("second");
  }

  @Test
  void containerDeleteContainerRemovesProjectBucketDirectory() {
    containerClient().deleteContainer("42");

    verify(operator).removeAll("prj-42/");
  }

  @Test
  void perBucketDeleteAllInSingleBucketModeUsesDefaultBucket() {
    when(featureFlagHandler.isEnabled(FeatureFlag.SINGLE_BUCKET)).thenReturn(true);
    when(operatorFactory.forBucket("rp-bucket")).thenReturn(operator);

    perBucketClient().deleteAll(List.of("project-data/1/attachment"));

    verify(operator).delete("project-data/1/attachment");
  }

  @Test
  void perBucketDeleteAllInMultiBucketModeUsesProjectBucket() {
    when(featureFlagHandler.isEnabled(FeatureFlag.SINGLE_BUCKET)).thenReturn(false);
    when(operatorFactory.forBucket("prj-42")).thenReturn(projectOperator);

    perBucketClient().deleteAll(List.of("42/2025-1/attachment"));

    verify(projectOperator).delete("2025-1/attachment");
  }

  @Test
  void perBucketDeleteContainerRemovesWholeBucket() {
    when(operatorFactory.forBucket("prj-42")).thenReturn(projectOperator);

    perBucketClient().deleteContainer("42");

    verify(projectOperator).removeAll("/");
  }
}
