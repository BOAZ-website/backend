package com.boaz.backend.domain.archive.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class ArchivePageResponse {

    @JsonProperty("current_page")
    private int currentPage;

    @JsonProperty("total_pages")
    private int totalPages;

    private int size; 

    @JsonProperty("current_size")
    private int currentSize;

    @JsonProperty("total_size")
    private long totalSize;

    @JsonProperty("has_previous")
    private boolean hasPrevious;

    @JsonProperty("has_next")
    private boolean hasNext;

    private List<ArchiveItemResponse> posts;

    public static ArchivePageResponse fromPage(Page<ArchiveItemResponse> page) {
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