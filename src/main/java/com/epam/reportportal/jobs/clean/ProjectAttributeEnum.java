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

package com.epam.reportportal.jobs.clean;

/**
 * Enum with a list of basic required project parameters
 *
 * @author Andrey Plisunov
 */
public enum ProjectAttributeEnum {

	INTERRUPT_JOB_TIME("job.interruptJobTime"),
	KEEP_LAUNCHES("job.keepLaunches"),
	KEEP_LOGS("job.keepLogs"),
	KEEP_SCREENSHOTS("job.keepScreenshots");

	private String attribute;

	ProjectAttributeEnum(String attribute) {
		this.attribute = attribute;
	}

	public String getAttribute() {
		return attribute;
	}
}


