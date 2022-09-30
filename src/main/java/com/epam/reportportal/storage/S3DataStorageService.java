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

import org.jclouds.blobstore.BlobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * S3 storage service
 */
public class S3DataStorageService implements DataStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3DataStorageService.class);

    private final BlobStore blobStore;
    private final String bucketPrefix;
    private final String defaultBucketName;

    public S3DataStorageService(BlobStore blobStore, String bucketPrefix, String defaultBucketName) {
        this.blobStore = blobStore;
        this.bucketPrefix = bucketPrefix;
        this.defaultBucketName = defaultBucketName;
    }

    @Override
    public void delete(String filePath) throws Exception {
        Path targetPath = Paths.get(filePath);
        int nameCount = targetPath.getNameCount();

        String bucket;
        String objectName;

        if (nameCount > 1) {
            bucket = bucketPrefix + retrievePath(targetPath, 0, 1);
            objectName = retrievePath(targetPath, 1, nameCount);
        } else {
            bucket = defaultBucketName;
            objectName = retrievePath(targetPath, 0, 1);
        }

        try {
            blobStore.removeBlob(bucket, objectName);
        } catch (Exception e) {
            LOGGER.error("Unable to delete file '{}'", filePath, e);
            throw e;
        }
    }

    private String retrievePath(Path path, int beginIndex, int endIndex) {
        return String.valueOf(path.subpath(beginIndex, endIndex));
    }
}
