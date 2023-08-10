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

package com.epam.reportportal.model.activity;

import com.epam.reportportal.model.activity.enums.EventAction;
import com.epam.reportportal.model.activity.enums.EventObject;
import com.epam.reportportal.model.activity.enums.EventPriority;
import com.epam.reportportal.model.activity.enums.EventSubject;
import java.time.LocalDateTime;

/**
 * A model that represents the state of the Activity.
 *
 * @author Ryhor_Kukharenka
 */
public class Activity {

  private LocalDateTime createdAt;
  private EventAction action;
  private String eventName;
  private EventPriority priority;
  private Long objectId;
  private String objectName;
  private EventObject objectType;
  private Long projectId;
  private String projectName;
  private Long subjectId;
  private String subjectName;
  private EventSubject subjectType;
  private boolean isSavedEvent;

  public Activity() {
    this.isSavedEvent = true;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public EventAction getAction() {
    return action;
  }

  public void setAction(EventAction action) {
    this.action = action;
  }

  public String getEventName() {
    return eventName;
  }

  public void setEventName(String eventName) {
    this.eventName = eventName;
  }

  public EventPriority getPriority() {
    return priority;
  }

  public void setPriority(EventPriority priority) {
    this.priority = priority;
  }

  public Long getObjectId() {
    return objectId;
  }

  public void setObjectId(Long objectId) {
    this.objectId = objectId;
  }

  public String getObjectName() {
    return objectName;
  }

  public void setObjectName(String objectName) {
    this.objectName = objectName;
  }

  public EventObject getObjectType() {
    return objectType;
  }

  public void setObjectType(EventObject objectType) {
    this.objectType = objectType;
  }

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long projectId) {
    this.projectId = projectId;
  }

  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  public Long getSubjectId() {
    return subjectId;
  }

  public void setSubjectId(Long subjectId) {
    this.subjectId = subjectId;
  }

  public String getSubjectName() {
    return subjectName;
  }

  public void setSubjectName(String subjectName) {
    this.subjectName = subjectName;
  }

  public EventSubject getSubjectType() {
    return subjectType;
  }

  public void setSubjectType(EventSubject subjectType) {
    this.subjectType = subjectType;
  }

  public boolean isSavedEvent() {
    return isSavedEvent;
  }

  public void setSavedEvent(boolean savedEvent) {
    isSavedEvent = savedEvent;
  }

  public static ActivityBuilder builder() {
    return new ActivityBuilder();
  }


  /**
   * Activity builder.
   *
   * @author Ryhor_Kukharenka
   */
  public static class ActivityBuilder {

    private final Activity activity;

    private ActivityBuilder() {
      this.activity = new Activity();
    }

    public ActivityBuilder addCreatedNow() {
      activity.setCreatedAt(LocalDateTime.now());
      return this;
    }

    public ActivityBuilder addAction(EventAction action) {
      activity.setAction(action);
      return this;
    }

    public ActivityBuilder addEventName(String eventName) {
      activity.setEventName(eventName);
      return this;
    }

    public ActivityBuilder addPriority(EventPriority priority) {
      activity.setPriority(priority);
      return this;
    }

    public ActivityBuilder addObjectId(Long objectId) {
      activity.setObjectId(objectId);
      return this;
    }

    public ActivityBuilder addObjectName(String objectName) {
      activity.setObjectName(objectName);
      return this;
    }

    public ActivityBuilder addObjectType(EventObject objectType) {
      activity.setObjectType(objectType);
      return this;
    }

    public ActivityBuilder addProjectId(Long projectId) {
      activity.setProjectId(projectId);
      return this;
    }

    public ActivityBuilder addProjectName(String projectName) {
      activity.setProjectName(projectName);
      return this;
    }

    public ActivityBuilder addSubjectId(Long subjectId) {
      activity.setSubjectId(subjectId);
      return this;
    }

    public ActivityBuilder addSubjectName(String subjectName) {
      activity.setSubjectName(subjectName);
      return this;
    }

    public ActivityBuilder addSubjectType(EventSubject subjectType) {
      activity.setSubjectType(subjectType);
      return this;
    }

    public Activity build() {
      return activity;
    }

  }

}
