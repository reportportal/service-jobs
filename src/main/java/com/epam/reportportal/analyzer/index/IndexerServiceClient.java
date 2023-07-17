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

package com.epam.reportportal.analyzer.index;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public interface IndexerServiceClient {

	/**
	 * Remove documents with specified ids from index
	 *
	 * @param index Index to be cleaned
	 * @param ids   Document ids to be deleted from index
	 * @return Amount of deleted logs
	 */
	Long cleanIndex(Long index, List<Long> ids);

	/**
	 * Remove documents from index by index and log time range.
	 * @param index Index to be cleaned.
	 */
	void removeFromIndexLessThanLogDate(Long index, LocalDateTime lessThanDate);

	/**
	 * Remove documents from index by index and log time range.
	 * @param index Index to be cleaned
	 */
	void removeFromIndexLessThanLaunchDate(Long index, LocalDateTime lessThanDate);

	/**
	 * Delete index
	 *
	 * @param index Index to be deleted
	 */
	void deleteIndex(Long index);

	/**
	 * Removes suggest index
	 *
	 * @param projectId Project/index id
	 */
	void removeSuggest(Long projectId);

}
