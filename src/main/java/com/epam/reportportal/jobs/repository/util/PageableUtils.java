package com.epam.reportportal.jobs.repository.util;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:budaevqwerty@gmail.com">Ivan Budayeu</a>
 */
public interface PageableUtils {

	static <T> void iterateOverContent(Integer limit, BiFunction<Integer, Long, List<T>> provider, Consumer<List<T>> handler) {
		long offset = 0;
		List<T> content = provider.apply(limit, offset);

		while (!content.isEmpty()) {
			handler.accept(content);
			offset += limit;
			content = provider.apply(limit, offset);
		}
	}
}
