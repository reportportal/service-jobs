package com.epam.reportportal.config;

import com.epam.reportportal.storage.DataStorageService;
import com.epam.reportportal.storage.LocalDataStorageService;
import com.epam.reportportal.storage.S3DataStorageService;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Module;
import org.jclouds.ContextBuilder;
import org.jclouds.aws.s3.config.AWSS3HttpApiModule;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.ContainerNotFoundException;
import org.jclouds.rest.ConfiguresHttpApi;
import org.jclouds.s3.S3Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Set;

@Configuration
public class DataStorageConfig {

	@ConfiguresHttpApi
	private static class CustomBucketToRegion extends AWSS3HttpApiModule {
		private final String region;

		public CustomBucketToRegion(String region) {
			this.region = region;
		}

		@Override
		@SuppressWarnings("Guava")
		protected CacheLoader<String, Optional<String>> bucketToRegion(Supplier<Set<String>> regionSupplier, S3Client client) {
			Set<String> regions = regionSupplier.get();
			if (regions.isEmpty()) {
				return new CacheLoader<>() {

					@Override
					@SuppressWarnings({ "Guava", "NullableProblems" })
					public Optional<String> load(String bucket) {
						if (CustomBucketToRegion.this.region != null) {
							return Optional.of(CustomBucketToRegion.this.region);
						}
						return Optional.absent();
					}

					@Override
					public String toString() {
						return "noRegions()";
					}
				};
			} else if (regions.size() == 1) {
				final String onlyRegion = Iterables.getOnlyElement(regions);
				return new CacheLoader<>() {
					@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
					final Optional<String> onlyRegionOption = Optional.of(onlyRegion);

					@Override
					@SuppressWarnings("NullableProblems")
					public Optional<String> load(String bucket) {
						if (CustomBucketToRegion.this.region != null) {
							return Optional.of(CustomBucketToRegion.this.region);
						}
						return onlyRegionOption;
					}

					@Override
					public String toString() {
						return "onlyRegion(" + onlyRegion + ")";
					}
				};
			} else {
				return new CacheLoader<>() {
					@Override
					@SuppressWarnings("NullableProblems")
					public Optional<String> load(String bucket) {
						if (CustomBucketToRegion.this.region != null) {
							return Optional.of(CustomBucketToRegion.this.region);
						}
						try {
							return Optional.fromNullable(client.getBucketLocation(bucket));
						} catch (ContainerNotFoundException e) {
							return Optional.absent();
						}
					}

					@Override
					public String toString() {
						return "bucketToRegion()";
					}
				};
			}
		}
	}

	@Bean
	@ConditionalOnProperty(name = "datastore.type", havingValue = "filesystem")
	public DataStorageService localDataStore(@Value("${datastore.default.path:/data/store}") String storagePath) {
		return new LocalDataStorageService(storagePath);
	}

	@Bean
	@ConditionalOnProperty(name = "datastore.type", havingValue = "minio")
	public BlobStore minioBlobStore(@Value("${datastore.minio.accessKey}") String accessKey,
			@Value("${datastore.minio.secretKey}") String secretKey, @Value("${datastore.minio.endpoint}") String endpoint) {

		BlobStoreContext blobStoreContext = ContextBuilder.newBuilder("s3")
				.endpoint(endpoint)
				.credentials(accessKey, secretKey)
				.buildView(BlobStoreContext.class);

		return blobStoreContext.getBlobStore();
	}

	@Bean
	@ConditionalOnProperty(name = "datastore.type", havingValue = "minio")
	public DataStorageService minioDataStore(@Autowired BlobStore blobStore, @Value("${datastore.minio.bucketPrefix}") String bucketPrefix,
			@Value("${datastore.minio.defaultBucketName}") String defaultBucketName) {
		return new S3DataStorageService(blobStore, bucketPrefix, defaultBucketName);
	}

	@Bean
	@ConditionalOnProperty(name = "datastore.type", havingValue = "s3")
	public BlobStore blobStore(@Value("${datastore.s3.accessKey}") String accessKey, @Value("${datastore.s3.secretKey}") String secretKey,
			@Value("${datastore.s3.region}") String region) {
		Iterable<Module> modules = ImmutableSet.of(new CustomBucketToRegion(region));

		BlobStoreContext blobStoreContext = ContextBuilder.newBuilder("aws-s3")
				.modules(modules)
				.credentials(accessKey, secretKey)
				.buildView(BlobStoreContext.class);

		return blobStoreContext.getBlobStore();
	}

	@Bean
	@Primary
	@ConditionalOnProperty(name = "datastore.type", havingValue = "s3")
	public DataStorageService s3DataStore(@Autowired BlobStore blobStore, @Value("${datastore.s3.bucketPrefix}") String bucketPrefix,
			@Value("${datastore.s3.defaultBucketName}") String defaultBucketName) {
		return new S3DataStorageService(blobStore, bucketPrefix, defaultBucketName);
	}
}
