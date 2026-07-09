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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.opendal.Operator;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

/**
 * Builds and caches one OpenDAL {@link Operator} per S3(-compatible) bucket.
 *
 * <p>OpenDAL binds a single bucket to an {@link Operator} at construction time, while ReportPortal may address many
 * buckets (one per project, unless the {@code singleBucket} feature flag is enabled) through one
 * endpoint/credentials pair. When IAM credentials are used, cached operators for a bucket are rebuilt once the
 * underlying session token nears expiration; statically configured credentials never expire, so those operators are
 * cached for the lifetime of the application.
 */
public class S3OperatorFactory {

  private static final String ACCESS_KEY_ID = "access_key_id";
  private static final String SECRET_ACCESS_KEY = "secret_access_key";
  private static final String BUCKET = "bucket";

  private final Map<String, String> baseConfig;
  private final AwsCredentialsProvider credentialsProvider;
  private final ConcurrentHashMap<String, CachedOperator> operators = new ConcurrentHashMap<>();

  public S3OperatorFactory(Map<String, String> baseConfig) {
    this(baseConfig, null);
  }

  public S3OperatorFactory(Map<String, String> baseConfig, AwsCredentialsProvider credentialsProvider) {
    this.baseConfig = baseConfig;
    this.credentialsProvider = credentialsProvider;
  }

  /**
   * Returns the {@link Operator} bound to the given bucket, building (or rebuilding, if IAM credentials have expired)
   * one if necessary.
   *
   * @param bucket bucket name to operate on
   * @return {@link Operator} scoped to {@code bucket}
   */
  public Operator forBucket(String bucket) {
    CachedOperator cached = operators.get(bucket);
    if (cached == null || Instant.now().isAfter(cached.expiresAt())) {
      cached = buildOperator(bucket);
      operators.put(bucket, cached);
    }
    return cached.operator();
  }

  private CachedOperator buildOperator(String bucket) {
    Map<String, String> config = new HashMap<>(baseConfig);
    config.put(BUCKET, bucket);
    Instant expiresAt = Instant.MAX;

    if (credentialsProvider != null) {
      AwsCredentials credentials = credentialsProvider.resolveCredentials();
      config.put(ACCESS_KEY_ID, credentials.accessKeyId());
      config.put(SECRET_ACCESS_KEY, credentials.secretAccessKey());
      if (credentials instanceof AwsSessionCredentials sessionCredentials) {
        expiresAt = sessionCredentials.expirationTime().orElseGet(() -> Instant.now().plusSeconds(3600));
      }
    }

    return new CachedOperator(Operator.of("s3", config), expiresAt);
  }

  private record CachedOperator(Operator operator, Instant expiresAt) {

  }
}
