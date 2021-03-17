package com.epam.reportportal.jobs.entity;

import java.util.Optional;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class EntityUtils {

	public static Optional<Attribute> extractAttribute(Project project, String attribute) {
		return project.getAttributes().stream().filter(pa -> pa.getAttribute().equalsIgnoreCase(attribute)).findFirst();
	}

	public static Optional<String> extractAttributeValue(Project project, String attribute) {
		return extractAttribute(project, attribute).map(Attribute::getValue);
	}

}
