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
import org.apache.opendal.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

/**
 * S3(-compatible) storage service, backed by OpenDAL.
 */
public class DataStoreClient implements DataStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(DataStoreClient.class);

  private final S3OperatorFactory operatorFactory;
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
    this.operatorFactory = operatorFactory;
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
      List<String> keys = new ArrayList<>(paths);
      if (legacyEncodedKeyFallback) {
        for (String path : paths) {
          String legacyKey = urlEncodeKey(path);
          if (!legacyKey.equals(path)) {
            keys.add(legacyKey);
          }
        }
      }
      removeFiles(defaultBucketName, keys);
    } else {
      Map<String, List<String>> bucketPathMap = BucketPathResolver.groupByBucket(paths);
      if (legacyEncodedKeyFallback) {
        for (List<String> bucketPaths : bucketPathMap.values()) {
          List<String> legacyKeys = new ArrayList<>();
          for (String cutPath : bucketPaths) {
            String legacyKey = urlEncodeKey(cutPath);
            if (!legacyKey.equals(cutPath)) {
              legacyKeys.add(legacyKey);
            }
          }
          bucketPaths.addAll(legacyKeys);
        }
      }
      for (Map.Entry<String, List<String>> bucketPaths : bucketPathMap.entrySet()) {
        removeFiles(bucketPrefix + bucketPaths.getKey() + bucketPostfix, bucketPaths.getValue());
      }
    }
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
      operatorFactory.forBucket(bucketPrefix + containerName + bucketPostfix).removeAll("/");
    } catch (Exception e) {
      LOGGER.warn("Exception {} is occurred during deleting container", e.getMessage());
    }
  }

  private void removeFiles(String bucketName, List<String> paths) {
    Operator operator = operatorFactory.forBucket(bucketName);
    for (String path : paths) {
      try {
        operator.delete(path);
      } catch (Exception e) {
        LOGGER.warn("Exception {} is occurred during deleting file", e.getMessage());
      }
    }
  }

}
