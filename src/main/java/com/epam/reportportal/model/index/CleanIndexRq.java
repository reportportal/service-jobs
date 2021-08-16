package com.epam.reportportal.model.index;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CleanIndexRq {

	@JsonProperty("project")
	private Long projectId;

	@JsonProperty("ids")
	private List<Long> logIds;

	public CleanIndexRq() {
	}

	public CleanIndexRq(Long projectId, List<Long> logIds) {
		this.projectId = projectId;
		this.logIds = logIds;
	}

	public Long getProjectId() {
		return projectId;
	}

	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}

	public List<Long> getLogIds() {
		return logIds;
	}

	public void setLogIds(List<Long> logIds) {
		this.logIds = logIds;
	}
}
