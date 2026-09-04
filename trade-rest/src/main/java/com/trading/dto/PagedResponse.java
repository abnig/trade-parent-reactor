package com.trading.dto;

import java.util.List;

import com.trading.repository.PageRequest;

public class PagedResponse<T> {

    private final List<T> content;
    private final long page;
    private final int size;
    private final long totalElements;
    private final long totalPages;
    private final boolean first;
    private final boolean last;

    private PagedResponse(List<T> content, long page, int size, long totalElements,
                          long totalPages, boolean first, boolean last) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.first = first;
        this.last = last;
    }

    public static <T> PagedResponse<T> of(List<T> content, PageRequest pageRequest, long totalElements) {
        long totalPages = totalElements == 0 ? 0 : ((totalElements - 1) / pageRequest.size()) + 1;
        boolean first = pageRequest.page() == 0;
        boolean last = totalPages == 0 || pageRequest.page() >= totalPages - 1;
        return new PagedResponse<>(content, pageRequest.page(), pageRequest.size(), totalElements,
                totalPages, first, last);
    }

    public List<T> getContent() { return content; }
    public long getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public long getTotalPages() { return totalPages; }
    public boolean isFirst() { return first; }
    public boolean isLast() { return last; }
}
