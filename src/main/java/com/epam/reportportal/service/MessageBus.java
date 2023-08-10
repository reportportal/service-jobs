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

package com.epam.reportportal.service;

import com.epam.reportportal.model.activity.ActivityEvent;
import java.util.List;

/**
 * MessageBus is an abstraction for dealing with events over external event-streaming system.
 *
 * @author Ryhor_Kukharenka
 */
public interface MessageBus {

  void publishActivity(ActivityEvent event);

  void sendNotificationEmail(List<String> recipients);

}
