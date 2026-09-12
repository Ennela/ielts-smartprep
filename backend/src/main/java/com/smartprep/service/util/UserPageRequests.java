package com.smartprep.service.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Page requests for the user-facing list endpoints.
 *
 * <p>One place for the clamp: a negative page reads as the first, a size outside 1..100
 * is pulled back into it. The admin controllers cap at the same 100; user endpoints used
 * to take whatever they were sent, which for the history lists meant no paging at all.
 */
public final class UserPageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private UserPageRequests() {}

    public static PageRequest of(int page, int size, Sort sort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }
}
