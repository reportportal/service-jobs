package com.epam.reportportal.jobs.entity;

import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class Project {

	private Long id;

	private String name;

	private Set<Attribute> attributes = new HashSet<>();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Set<Attribute> getAttributes() {
		return attributes;
	}

	public void setAttributes(Set<Attribute> attributes) {
		this.attributes = attributes;
	}

	@Override
	public String toString() {
		return "Project{" + "id=" + id + ", name='" + name + '\'' + ", attributes=" + attributes + '}';
	}
}
