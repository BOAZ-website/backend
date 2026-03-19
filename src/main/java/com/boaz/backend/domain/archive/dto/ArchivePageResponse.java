package com.boaz.backend.domain.archive.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class ArchivePageResponse {

    private int currentPage;
    private int totalPages;
    private int size; 
    private int currentSize;
    private long totalSize;
    private boolean hasPrevious;
    private boolean hasNext;
    private List<ArchiveItemResponse> posts;

    public static ArchivePageResponse from(Page<ArchiveItemResponse> page) {
        return ArchivePageResponse.builder()
            .currentPage(page.getNumber() + 1)  // 현재 페이지 번호 1부터 시작하도록
            .totalPages(page.getTotalPages())
            .size(page.getSize())
            .currentSize(page.getNumberOfElements())
            .totalSize(page.getTotalElements())
            .hasPrevious(page.hasPrevious())
            .hasNext(page.hasNext())
            .posts(page.getContent())   // Page 객체 안에 있는 실제 데이터 리스트를 꺼내 posts 필드에 넣음 
            .build();
    }

}