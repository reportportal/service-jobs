/*
 * Copyright 2019 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.storage;

import com.epam.reportportal.utils.FeatureFlag;
import com.epam.reportportal.utils.FeatureFlagHandler;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.opendal.Operator;
import org.springframework.util.CollectionUtils;

/**
 * Blob storage service, backed by OpenDAL.
 *
 * <p>Works in one of two modes:
 * <ul>
 *   <li>per-bucket (S3, S3-compatible, SeaweedFS): every bucket gets its own {@link Operator} from the
 *   {@link S3OperatorFactory}, and keys are relative to that bucket;</li>
 *   <li>single container (Azure Blob Storage, Google Cloud Storage): one {@link Operator} is bound to one
 *   container/bucket, matching the layout service-api writes for these backends. With the {@code singleBucket}
 *   feature flag enabled, stored paths are used as-is inside the container; otherwise every bucket is a top-level
 *   directory inside the container.</li>
 * </ul>
 */
@Slf4j
public class DataStoreClient implements DataStore {

  private static final String PATH_DELIMITER = "/";

  private final BucketAccess bucketAccess;
  private final String bucketPrefix;
  private final String bucketPostfix;
  private final String defaultBucketName;
  private final boolean legacyEncodedKeyFallback;

  private final FeatureFlagHandler featureFlagHandler;

  /**
   * Creates instance of {@link DataStoreClient}.
   *
   * @param operatorFactory    {@link S3OperatorFactory} providing a per-bucket {@link Operator}
   * @param bucketPrefix       Prefix for bucket name
   * @param bucketPostfix      Postfix for bucket name
   * @param defaultBucketName  Name for the default bucket(plugins, etc.)
   * @param featureFlagHandler {@link FeatureFlagHandler}
   */
  public DataStoreClient(S3OperatorFactory operatorFactory, String bucketPrefix, String bucketPostfix,
      String defaultBucketName, FeatureFlagHandler featureFlagHandler) {
    this(operatorFactory, bucketPrefix, bucketPostfix, defaultBucketName, featureFlagHandler, false);
  }

  public DataStoreClient(S3OperatorFactory operatorFactory, String bucketPrefix, String bucketPostfix,
      String defaultBucketName, FeatureFlagHandler featureFlagHandler,
      boolean legacyEncodedKeyFallback) {
    this(new PerBucketAccess(operatorFactory), bucketPrefix, bucketPostfix, defaultBucketName,
        featureFlagHandler, legacyEncodedKeyFallback);
  }

  /**
   * Creates instance of {@link DataStoreClient} working inside a single container/bucket.
   *
   * @param containerOperator  {@link Operator} bound to the container/bucket that holds all files
   * @param bucketPrefix       Prefix for bucket name
   * @param bucketPostfix      Postfix for bucket name
   * @param defaultBucketName  Name for the default bucket(plugins, etc.)
   * @param featureFlagHandler {@link FeatureFlagHandler}
   */
  public DataStoreClient(Operator containerOperator, String bucketPrefix, String bucketPostfix,
      String defaultBucketName, FeatureFlagHandler featureFlagHandler) {
    this(new ContainerAccess(containerOperator), bucketPrefix, bucketPostfix, defaultBucketName,
        featureFlagHandler, false);
  }

  private DataStoreClient(BucketAccess bucketAccess, String bucketPrefix, String bucketPostfix,
      String defaultBucketName, FeatureFlagHandler featureFlagHandler,
      boolean legacyEncodedKeyFallback) {
    this.bucketAccess = bucketAccess;
    this.bucketPrefix = bucketPrefix;
    this.bucketPostfix = bucketPostfix;
    this.defaultBucketName = defaultBucketName;
    this.featureFlagHandler = featureFlagHandler;
    this.legacyEncodedKeyFallback = legacyEncodedKeyFallback;
  }

  @Override
  public void deleteAll(List<String> paths) {
    if (CollectionUtils.isEmpty(paths)) {
      return;
    }
    if (featureFlagHandler.isEnabled(FeatureFlag.SINGLE_BUCKET)) {
      removeFiles(bucketAccess.singleBucketName(defaultBucketName), withLegacyKeys(paths));
    } else {
      deleteFromProjectBuckets(paths);
    }
  }

  private void deleteFromProjectBuckets(List<String> paths) {
    Map<String, List<String>> bucketPathMap = BucketPathResolver.groupByBucket(paths);
    for (Map.Entry<String, List<String>> bucketPaths : bucketPathMap.entrySet()) {
      removeFiles(bucketPrefix + bucketPaths.getKey() + bucketPostfix,
          withLegacyKeys(bucketPaths.getValue()));
    }
  }

  private List<String> withLegacyKeys(List<String> keys) {
    if (!legacyEncodedKeyFallback) {
      return keys;
    }
    List<String> keysWithLegacyFallback = new ArrayList<>(keys);
    for (String key : keys) {
      String legacyKey = urlEncodeKey(key);
      if (!legacyKey.equals(key)) {
        keysWithLegacyFallback.add(legacyKey);
      }
    }
    return keysWithLegacyFallback;
  }

  private String urlEncodeKey(String key) {
    String[] segments = key.split("/", -1);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) {
        sb.append('/');
      }
      sb.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
    }
    return sb.toString();
  }

  @Override
  public void deleteContainer(String containerName) {
    try {
      bucketAccess.removeBucket(bucketPrefix + containerName + bucketPostfix);
    } catch (Exception e) {
      log.warn("Exception {} is occurred during deleting container", e.getMessage());
    }
  }

  private void removeFiles(String bucketName, List<String> paths) {
    for (String path : paths) {
      try {
        bucketAccess.delete(bucketName, path);
      } catch (Exception e) {
        log.warn("Exception {} is occurred during deleting file", e.getMessage());
      }
    }
  }

  /**
   * Maps a logical bucket and a key inside it onto OpenDAL operations.
   */
  private interface BucketAccess {

    /**
     * @param defaultBucketName configured default bucket name
     * @return bucket to use when the {@code singleBucket} feature flag is enabled
     */
    String singleBucketName(String defaultBucketName);

    void delete(String bucket, String key);

    void removeBucket(String bucket);
  }

  /**
   * One {@link Operator} per bucket, keys are relative to the bucket.
   */
  private record PerBucketAccess(S3OperatorFactory operatorFactory) implements BucketAccess {

    @Override
    public String singleBucketName(String defaultBucketName) {
      return defaultBucketName;
    }

    @Override
    public void delete(String bucket, String key) {
      operatorFactory.forBucket(bucket).delete(key);
    }

    @Override
    public void removeBucket(String bucket) {
      operatorFactory.forBucket(bucket).removeAll(PATH_DELIMITER);
    }
  }

  /**
   * One {@link Operator} bound to a container, buckets are top-level directories inside it. An empty bucket name
   * stands for the container root.
   */
  private record ContainerAccess(Operator operator) implements BucketAccess {

    @Override
    public String singleBucketName(String defaultBucketName) {
      return "";
    }

    @Override
    public void delete(String bucket, String key) {
      operator.delete(bucket.isEmpty() ? key : bucket + PATH_DELIMITER + key);
    }

    @Override
    public void removeBucket(String bucket) {
      operator.removeAll(bucket + PATH_DELIMITER);
    }
  }
}
