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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits object paths of the form {@code <bucket>/<key...>} into a bucket -> keys map, used by both
 * {@link DataStoreClient} and {@link LocalDataStore} to group deletions by their owning bucket/container.
 */
final class BucketPathResolver {

  private BucketPathResolver() {
  }

  static Map<String, List<String>> groupByBucket(List<String> paths) {
    Map<String, List<String>> bucketPathMap = new HashMap<>();
    for (String path : paths) {
      Path targetPath = Paths.get(path);
      String bucket = String.valueOf(targetPath.subpath(0, 1));
      String cutPath = String.valueOf(targetPath.subpath(1, targetPath.getNameCount()));
      bucketPathMap.computeIfAbsent(bucket, k -> new ArrayList<>()).add(cutPath);
    }
    return bucketPathMap;
  }
}
