package com.epam.reportportal.config;

import com.epam.reportportal.storage.DataStorageService;
import com.epam.reportportal.storage.LocalDataStorageService;
import com.epam.reportportal.storage.MinioDataStorageService;
import io.minio.MinioClient;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DataStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "datastore.type", havingValue = "filesystem")
    public DataStorageService localDataStore(@Value("${datastore.default.path:/data/store}") String storagePath) {
        return new LocalDataStorageService(storagePath);
    }

    @Bean
    @ConditionalOnProperty(name = "datastore.type", havingValue = "minio")
    public MinioClient minioClient(@Value("${datastore.minio.endpoint}") String endpoint,
                                   @Value("${datastore.minio.accessKey}") String accessKey, @Value("${datastore.minio.secretKey}") String secretKey,
                                   @Value("${datastore.minio.region}") String region) throws InvalidPortException, InvalidEndpointException {
        return new MinioClient(endpoint, accessKey, secretKey, region);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "datastore.type", havingValue = "minio")
    public DataStorageService minioDataStore(@Autowired MinioClient minioClient,
                                                  @Value("${datastore.minio.bucketPrefix}") String bucketPrefix,
                                                  @Value("${datastore.minio.defaultBucketName}") String defaultBucketName) {
        return new MinioDataStorageService(minioClient, bucketPrefix, defaultBucketName);
    }
}
