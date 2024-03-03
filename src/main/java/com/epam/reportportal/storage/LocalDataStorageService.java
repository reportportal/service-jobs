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
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jclouds.blobstore.BlobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

/**
 * Local storage service
 */
public class LocalDataStorageService implements DataStorageService {

  private static final Logger LOGGER = LoggerFactory.getLogger(LocalDataStorageService.class);

  private final BlobStore blobStore;

  private final FeatureFlagHandler featureFlagHandler;

  private final String baseDirectory;

  private final String bucketPrefix;

  private final String bucketPostfix;

  private final String defaultBucketName;

  private static final String PROJECT_PREFIX = "project-data";

  private static final String SINGLE_BUCKET_NAME = "store";

  public LocalDataStorageService(BlobStore blobStore, FeatureFlagHandler featureFlagHandler,
      String baseDirectory, String bucketPrefix, String bucketPostfix, String defaultBucketName) {
    this.blobStore = blobStore;
    this.featureFlagHandler = featureFlagHandler;
    this.baseDirectory = baseDirectory;
    this.bucketPrefix = bucketPrefix;
    this.bucketPostfix = bucketPostfix;
    this.defaultBucketName = defaultBucketName;
  }

  @Override
  public void deleteAll(List<String> paths) throws Exception {
    if (CollectionUtils.isEmpty(paths)) {
      return;
    }
    if (featureFlagHandler.isEnabled(FeatureFlag.SINGLE_BUCKET)) {
      Map<String, List<String>> bucketPathMap = retrieveBucketPathMap(paths);
      for (Map.Entry<String, List<String>> bucketPaths : bucketPathMap.entrySet()) {
        removeFiles(
            defaultBucketName,
            bucketPaths.getValue().stream().map(s -> bucketPaths.getKey() + "/" + s).toList()
        );
        deleteEmptyDirs(Paths.get(baseDirectory, SINGLE_BUCKET_NAME, PROJECT_PREFIX));
      }
    } else {
      Map<String, List<String>> bucketPathMap = retrieveBucketPathMap(paths);
      for (Map.Entry<String, List<String>> bucketPaths : bucketPathMap.entrySet()) {
        removeFiles(bucketPrefix + bucketPaths.getKey() + bucketPostfix, bucketPaths.getValue());
        deleteEmptyDirs(Paths.get(baseDirectory, bucketPaths.getKey()));
      }
    }
  }

  @Override
  public void deleteContainer(String containerName) {
    try {
      blobStore.deleteContainer(containerName);
    } catch (Exception e) {
      LOGGER.warn("Exception {} is occurred during deleting container", e.getMessage());
    }
  }

  private Map<String, List<String>> retrieveBucketPathMap(List<String> paths) {
    Map<String, List<String>> bucketPathMap = new HashMap<>();
    for (String path : paths) {
      Path targetPath = Paths.get(path);
      int nameCount = targetPath.getNameCount();
      String bucket = retrievePath(targetPath, 0, 1);
      String cutPath = retrievePath(targetPath, 1, nameCount);
      if (bucketPathMap.containsKey(bucket)) {
        bucketPathMap.get(bucket).add(cutPath);
      } else {
        List<String> bucketPaths = new ArrayList<>();
        bucketPaths.add(cutPath);
        bucketPathMap.put(bucket, bucketPaths);
      }
    }

    return bucketPathMap;
  }

  private String retrievePath(Path path, int beginIndex, int endIndex) {
    return String.valueOf(path.subpath(beginIndex, endIndex));
  }

  private void removeFiles(String bucketName, List<String> paths) {
    try {
      blobStore.removeBlobs(bucketName, paths);
    } catch (Exception e) {
      LOGGER.warn("Exception {} is occurred during deleting file", e.getMessage());
    }
  }

  private void deleteEmptyDirs(Path dir) {
    if (!Files.isDirectory(dir)) {
      return;
    }

    // List all files/directories in the given directory
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path entry : stream) {
        deleteEmptyDirs(entry);
      }
    } catch (IOException e) {
      LOGGER.warn("Exception {} is occurred during checking directory", e.getMessage());
    }

    // Delete the directory if empty
    try {
      if (isDirectoryEmpty(dir)) {
        Files.delete(dir);
      }
    } catch (IOException e) {
      LOGGER.warn("Exception {} is occurred during deleting empty directory", e.getMessage());
    }
  }

  private boolean isDirectoryEmpty(Path dir) {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      return !stream.iterator().hasNext();
    } catch (IOException e) {
      LOGGER.warn("Exception {} is occurred during checking directory", e.getMessage());
      return false;
    }
  }

}
