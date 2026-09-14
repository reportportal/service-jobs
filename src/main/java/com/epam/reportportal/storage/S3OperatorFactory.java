/*
 * Copyright 2025 EPAM Systems
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.opendal.Operator;

/**
 * Builds and caches one OpenDAL {@link Operator} per S3(-compatible) bucket.
 *
 * <p>OpenDAL binds a single bucket to an {@link Operator} at construction time, while ReportPortal may address many
 * buckets (one per project, unless the {@code singleBucket} feature flag is enabled) through one
 * endpoint/credentials pair. When no static {@code access_key_id}/{@code secret_access_key} are supplied in the base
 * config, OpenDAL's S3 service resolves and refreshes credentials itself (environment variables, shared profile,
 * EC2/ECS/EKS instance metadata), so operators can be cached for the lifetime of the application regardless of
 * credential source.
 */
public class S3OperatorFactory {

  private static final String BUCKET = "bucket";

  private final Map<String, String> baseConfig;
  private final ConcurrentHashMap<String, Operator> operators = new ConcurrentHashMap<>();

  public S3OperatorFactory(Map<String, String> baseConfig) {
    this.baseConfig = baseConfig;
  }

  /**
   * Returns the {@link Operator} bound to the given bucket, building one if necessary.
   *
   * @param bucket bucket name to operate on
   * @return {@link Operator} scoped to {@code bucket}
   */
  public Operator forBucket(String bucket) {
    return operators.computeIfAbsent(bucket, this::buildOperator);
  }

  private Operator buildOperator(String bucket) {
    Map<String, String> config = new HashMap<>(baseConfig);
    config.put(BUCKET, bucket);
    return Operator.of("s3", config);
  }
}
