package com.pulseops.common.response;

import java.util.List;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.data.domain.Page;

@SuppressFBWarnings(
		value = "EI_EXPOSE_REP",
		justification = "The factory stores an immutable List.copyOf snapshot.")
public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last) {

	public static <T> PageResponse<T> from(Page<T> source) {
		return new PageResponse<>(
				List.copyOf(source.getContent()),
				source.getNumber(),
				source.getSize(),
				source.getTotalElements(),
				source.getTotalPages(),
				source.isFirst(),
				source.isLast());
	}
}
