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

package com.epam.reportportal.config;

import com.epam.reportportal.storage.DataStore;
import com.epam.reportportal.storage.DataStoreClient;
import com.epam.reportportal.storage.LocalDataStore;
import com.epam.reportportal.storage.S3OperatorFactory;
import com.epam.reportportal.utils.FeatureFlagHandler;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.opendal.Operator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

/**
 * Blob storage configuration.
 *
 * @author Dzianis_Shybeka
 */
@Configuration
public class DataStoreConfiguration {

  private static final String ACCESS_KEY_ID = "access_key_id";
  private static final String SECRET_ACCESS_KEY = "secret_access_key";
  private static final String ENDPOINT = "endpoint";
  private static final String REGION = "region";
  private static final String ROOT = "root";

  @Bean
  @ConditionalOnProperty(name = "datastore.type", havingValue = "filesystem")
  public Operator filesystemOperator(@Value("${datastore.path:/data/store}") String baseDirectory) {
    Map<String, String> config = new HashMap<>();
    config.put(ROOT, baseDirectory);
    return Operator.of("fs", config);
  }

  @Bean
  @ConditionalOnProperty(name = "datastore.type", havingValue = "filesystem")
  public DataStore localDataStore(@Autowired Operator filesystemOperator,
      FeatureFlagHandler featureFlagHandler,
      @Value("${datastore.path:/data/store}") String baseDirectory,
      @Value("${datastore.bucketPrefix}") String bucketPrefix,
      @Value("${datastore.bucketPostfix}") String bucketPostfix,
      @Value("${datastore.defaultBucketName}") String defaultBucketName) {
    return new LocalDataStore(filesystemOperator, featureFlagHandler, baseDirectory, bucketPrefix,
        bucketPostfix, defaultBucketName
    );
  }

  /**
   * Creates the {@link S3OperatorFactory} bean, that works with MinIO.
   *
   * @param accessKey accessKey to use
   * @param secretKey secretKey to use
   * @param endpoint  MinIO endpoint
   * @param region    Region to use
   * @return {@link S3OperatorFactory}
   */
  @Bean
  @ConditionalOnProperty(name = "datastore.type", havingValue = "s3-compatible")
  public S3OperatorFactory minioOperatorFactory(@Value("${datastore.accessKey}") String accessKey,
      @Value("${datastore.secretKey}") String secretKey,
      @Value("${datastore.endpoint}") String endpoint,
      @Value("${datastore.region:us-east-1}") String region) {
    return staticCredentialsOperatorFactory(accessKey, secretKey, endpoint, region);
  }

  /**
   * Creates DataStore bean to work with MinIO.
   *
   * @param minioOperatorFactory {@link S3OperatorFactory} object
   * @param bucketPrefix         Prefix for bucket name
   * @param bucketPostfix        Postfix for bucket name
   * @param defaultBucketName    Name of default bucket to use
   * @param featureFlagHandler   Instance of {@link FeatureFlagHandler} to check enabled features
   * @return {@link DataStore} object
   */
  @Bean
  @ConditionalOnProperty(name = "datastore.type", havingValue = "s3-compatible")
  public DataStore minioDataStore(@Autowired S3OperatorFactory minioOperatorFactory,
      @Value("${datastore.bucketPrefix}") String bucketPrefix,
      @Value("${datastore.bucketPostfix}") String bucketPostfix,
      @Value("${datastore.defaultBucketName}") String defaultBucketName,
      FeatureFlagHandler featureFlagHandler) {
    return new DataStoreClient(minioOperatorFactory, bucketPrefix, bucketPostfix, defaultBucketName,
        featureFlagHandler
    );
  }

  /**
   * Creates the {@link S3OperatorFactory} bean to work with SeaweedFS.
   *
   * <p>Uses the generic {@code s3} OpenDAL service with a custom {@code endpoint}. Unlike jclouds, OpenDAL's S3
   * service always signs requests with AWS Signature Version 4 and defaults to path-style bucket addressing, which is
   * exactly what SeaweedFS's S3 gateway expects, so no extra signing/virtual-host overrides are required here.
   *
   * @param accessKey access key
   * @param secretKey secret key
   * @param endpoint  SeaweedFS S3 gateway endpoint URL
   * @param region    region used for SigV4 credential scope (must match the backend expectation, e.g.
   *                  {@code us-east-1})
   * @return {@link S3OperatorFactory}
   */
  @Bean
  @ConditionalOnProperty(name = "datastore.type", havingValue = "seaweedfs")
  public S3OperatorFactory seaweedFsOperatorFactory(@Value("${datastore.accessKey}") String accessKey,
      @Value("${datastore.secretKey}") String secretKey,
      @Value("${datastore.endpoint}") String endpoint,
      @Value("${datastore.region:eu-central-1}") String region) {
    return staticCredentialsOperatorFactory(accessKey, secretKey, endpoint, region);
  }

  /**
   * Creates DataStore bean to work with SeaweedFS.
   *
   * @param seaweedFsOperatorFactory {@link S3OperatorFactory} object
   * @param bucketPrefix             prefix for bucket name
   * @param bucketPostfix            postfix for bucket name
   * @param defaultBucketName        name of default bucket to use
   * @param featureFlagHandler       instance of {@link FeatureFlagHandler} to check enabled features
   * @return {@link DataStore} object
   */
  @Bean
  @ConditionalOnProperty(name = "datastore.type", havingValue = "seaweedfs")
  public DataStore seaweedFsDataStore(@Autowired S3OperatorFactory seaweedFsOperatorFactory,
      @Value("${datastore.bucketPrefix}") String bucketPrefix,
      @Value("${datastore.bucketPostfix}") String bucketPostfix,
      @Value("${datastore.defaultBucketName}") String defaultBucketName,
      FeatureFlagHandler featureFlagHandler) {
    return new DataStoreClient(seaweedFsOperatorFactory, bucketPrefix, bucketPostfix, defaultBucketName,
        featureFlagHandler, true);
  }

  /**
   * Creates the {@link S3OperatorFactory} bean, that works with AWS S3.
   *
   * @param accessKey accessKey to use (optional, if not provided uses IAM credentials)
   * @param secretKey secretKey to use (optional, if not provided uses IAM credentials)
   * @param region    AWS S3 region to use.
   * @return {@link S3OperatorFactory}
   */
  @Bean
  @ConditionalOnProperty(name = "datastore.type", havingValue = "aws-s3")
  public S3OperatorFactory awsS3OperatorFactory(@Value("${datastore.accessKey:}") String accessKey,
      @Value("${datastore.secretKey:}") String secretKey,
      @Value("${datastore.region}") String region) {
    Map<String, String> config = new HashMap<>();
    config.put(REGION, region);

    if (StringUtils.isNotEmpty(accessKey) && StringUtils.isNotEmpty(secretKey)) {
      config.put(ACCESS_KEY_ID, accessKey);
      config.put(SECRET_ACCESS_KEY, secretKey);
      return new S3OperatorFactory(config);
    }

    return new S3OperatorFactory(config, DefaultCredentialsProvider.builder().build());
  }

  @Bean
  @Primary
  @ConditionalOnProperty(name = "datastore.type", havingValue = "aws-s3")
  public DataStore s3DataStore(@Autowired S3OperatorFactory awsS3OperatorFactory,
      @Value("${datastore.bucketPrefix}") String bucketPrefix,
      @Value("${datastore.bucketPostfix}") String bucketPostfix,
      @Value("${datastore.defaultBucketName}") String defaultBucketName,
      FeatureFlagHandler featureFlagHandler) {
    return new DataStoreClient(awsS3OperatorFactory, bucketPrefix, bucketPostfix, defaultBucketName,
        featureFlagHandler
    );
  }

  private S3OperatorFactory staticCredentialsOperatorFactory(String accessKey, String secretKey,
      String endpoint, String region) {
    Map<String, String> config = new HashMap<>();
    config.put(ACCESS_KEY_ID, accessKey);
    config.put(SECRET_ACCESS_KEY, secretKey);
    config.put(ENDPOINT, endpoint);
    config.put(REGION, region);
    return new S3OperatorFactory(config);
  }
}
