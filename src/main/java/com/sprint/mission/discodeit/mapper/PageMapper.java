package com.sprint.mission.discodeit.mapper;

import com.sprint.mission.discodeit.dto.response.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

@Component
public class PageMapper {

    public <T, R> PageResponse<R> toResponse(Slice<T> slice, Function<T, R> mapper) {
        List<R> content = slice.getContent().stream()
                .map(mapper)
                .toList();
        return new PageResponse<>(
                content,
                slice.getNumber(),
                slice.getSize(),
                slice.hasNext(),
                null,
                null
        );
    }

    public <T, R> PageResponse<R> toResponse(Page<T> page, Function<T, R> mapper) {
        List<R> content = page.getContent().stream()
                .map(mapper)
                .toList();
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.hasNext(),
                page.getTotalElements(),
                null
        );
    }

    public <T, R> PageResponse<R> toCursorResponse(
            Slice<T> slice,
            Function<T, R> mapper,
            Function<T, Instant> cursorExtractor
    ) {
        List<T> source = slice.getContent();
        List<R> content = source.stream()
                .map(mapper)
                .toList();
        Instant nextCursor = slice.hasNext() && !source.isEmpty()
                ? cursorExtractor.apply(source.get(source.size() - 1))
                : null;
        return new PageResponse<>(
                content,
                slice.getNumber(),
                slice.getSize(),
                slice.hasNext(),
                null,
                nextCursor
        );
    }
}
