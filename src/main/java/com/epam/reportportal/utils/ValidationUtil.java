/*
 * Copyright 2023 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.epam.reportportal.utils;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class which validate some information.
 *
 * @author Ryhor_Kukharenka
 */
public final class ValidationUtil {

  public static final Logger LOGGER = LoggerFactory.getLogger(ValidationUtil.class);

  private ValidationUtil() {
  }

  /**
   * Check retention period.
   *
   * @param retentionPeriod retentionPeriod
   * @return boolean value
   */
  public static boolean isInvalidRetentionPeriod(Long retentionPeriod) {
    if (Objects.isNull(retentionPeriod) || retentionPeriod <= 0) {
      LOGGER.warn("The parameter 'retentionPeriod' is not specified correctly.\n"
          + "Must be greater than 0.\n"
          + "The current value of the parameter = {}.", retentionPeriod);
      return true;
    }
    return false;
  }

}
