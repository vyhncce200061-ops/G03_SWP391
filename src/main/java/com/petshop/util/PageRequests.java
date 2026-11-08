package com.petshop.util;

import org.springframework.data.domain.PageRequest;

/** Keep client-supplied paging within the offset range supported by JPA. */
public final class PageRequests {
    private PageRequests() { }

    public static PageRequest bounded(int page, int size) {
        int safeSize = Math.max(1, Math.min(100, size));
        int safePage = Math.min(Math.max(0, page), Integer.MAX_VALUE / safeSize);
        return PageRequest.of(safePage, safeSize);
    }
}
