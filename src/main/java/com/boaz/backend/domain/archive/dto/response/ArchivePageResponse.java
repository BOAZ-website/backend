package com.boaz.backend.domain.archive.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class ArchivePageResponse {

    @Schema(description = "현재 페이지 번호", example = "5")
    private int currentPage;

    @Schema(description = "전체 페이지 수", example = "5")
    private int totalPages;

    @Schema(description = "페이지당 항목 수", example = "6")
    private int size; 

    @Schema(description = "현재 페이지 항목 수", example ="3")
    private int currentSize;

    @Schema(description = "전체 항목 수", example = "33")
    private long totalSize;

    @Schema(description = "이전 페이지 존재 여부", example = "true")
    private boolean hasPrevious;

    @Schema(description = "다음 페이지 존재 여부", example = "false")
    private boolean hasNext;

    @Schema(description = "아카이브 목록")
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