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

import java.io.File;
import java.util.List;
import javax.lang.model.type.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Local storage service
 */
public class LocalDataStorageService implements DataStorageService {

  private static final Logger LOGGER = LoggerFactory.getLogger(LocalDataStorageService.class);

  private final String storageRootPath;

  public LocalDataStorageService(String storageRootPath) {
    this.storageRootPath = storageRootPath;
  }

  @Override
  public void deleteAll(List<String> paths) throws IOException {
    for (String path : paths) {
      try {
        Files.deleteIfExists(Paths.get(storageRootPath, path));
      } catch (IOException e) {
        LOGGER.error("Unable to delete file '{}'", path, e);
        throw e;
      }
    }
  }

  @Override
  public void deleteContainer(String containerName) throws IOException{
    try {
      Files.deleteIfExists(Paths.get(storageRootPath, containerName));
    } catch (IOException e) {
      LOGGER.error("Unable to delete container '{}'", containerName, e);
      throw e;
    }
  }
}
