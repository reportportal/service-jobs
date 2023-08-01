/*
 * Copyright 2023 EPAM Systems
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

package com.epam.reportportal.model;

import java.util.Map;

/**
 * @author Andrei Piankouski
 */
public class EmailNotificationRequest {

  private String recipient;

  private String template;

  private Map<String, Object> params;

  public EmailNotificationRequest(String recipient, String template) {
    this.recipient = recipient;
    this.template = template;
  }

  public String getRecipient() {
    return recipient;
  }

  public void setRecipient(String recipient) {
    this.recipient = recipient;
  }

  public String getTemplate() {
    return template;
  }

  public void setTemplate(String template) {
    this.template = template;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public void setParams(Map<String, Object> params) {
    this.params = params;
  }

  @Override
  public String toString() {
    return "EmailNotificationRequest{" +
        "recipient='" + recipient + '\'' +
        ", template='" + template + '\'' +
        ", params=" + params +
        '}';
  }
}
