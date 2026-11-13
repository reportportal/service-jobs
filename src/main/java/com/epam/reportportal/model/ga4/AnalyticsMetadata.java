/*
 * Copyright 2026 EPAM Systems
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

package com.epam.reportportal.model.ga4;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents the {@code metadata} object stored per row in the {@code analytics_data} table for
 * {@code DEFECT_UPDATE_STATISTICS} entries.
 */
@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalyticsMetadata {

  @JsonProperty("organizationId")
  private Long organizationId;

  @JsonProperty("analyzerEnabled")
  private boolean analyzerEnabled;

  @JsonProperty("autoAnalysisOn")
  private boolean autoAnalysisOn;

  @JsonProperty("userAnalyzed")
  private int userAnalyzed;

  @JsonProperty("sentToAnalyze")
  private int sentToAnalyze;

  @JsonProperty("skipped")
  private int skipped;

  @JsonProperty("analyzed")
  private int analyzed;

  @JsonProperty("version")
  private String version;

}
