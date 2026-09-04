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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents the {@code params} object of a GA4 Measurement Protocol event.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Ga4EventParams {

  @JsonProperty("organization_id")
  private final String organizationId;

  @JsonProperty("category")
  private final String category;

  @JsonProperty("instanceID")
  private final String instanceId;

  @JsonProperty("timestamp")
  private final long timestamp;

  @JsonProperty("version")
  private final String version;

  @JsonProperty("type")
  private final String type;

  @JsonProperty("number")
  private final String number;

  @JsonProperty("auto_analysis")
  private final String autoAnalysis;

  @JsonProperty("status")
  private final String status;

}
