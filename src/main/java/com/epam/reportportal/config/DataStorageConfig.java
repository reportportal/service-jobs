package com.epam.reportportal.config;

import com.epam.reportportal.storage.DataStorageService;
import com.epam.reportportal.storage.LocalDataStorageService;
import com.epam.reportportal.storage.S3DataStorageService;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
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
    @ConditionalOnProperty(name = "datastore.type", havingValue = "s3")
    public BlobStore blobStore(@Value("${datastore.s3.endpoint}") String endpoint,
            @Value("${datastore.s3.accessKey}") String accessKey, @Value("${datastore.s3.secretKey}") String secretKey) {
        BlobStoreContext blobStoreContext = ContextBuilder.newBuilder("s3")
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .apiVersion("4")
                .buildView(BlobStoreContext.class);

        return blobStoreContext.getBlobStore();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "datastore.type", havingValue = "s3")
    public DataStorageService s3DataStore(@Autowired BlobStore blobStore,
                                                  @Value("${datastore.s3.bucketPrefix}") String bucketPrefix,
                                                  @Value("${datastore.s3.defaultBucketName}") String defaultBucketName) {
        return new S3DataStorageService(blobStore, bucketPrefix, defaultBucketName);
    }
}
