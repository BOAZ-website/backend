package com.boaz.backend.domain.archive.controller;

import com.boaz.backend.domain.archive.dto.ArchivePageResponse;
import com.boaz.backend.domain.archive.entity.ArchiveCategory;
import com.boaz.backend.domain.archive.service.ArchiveService;
import com.boaz.backend.global.common.ApiResponse;
import com.boaz.backend.global.common.enums.Track;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Archiving", description = "아카이빙 조회 API")
@RestController
@RequestMapping("/api/v1/archiving")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    @Operation(summary = "프로젝트 아카이빙 목록 조회")
    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<ArchivePageResponse>> getProjects(
        @RequestParam(defaultValue = "전부문") Track track, 
        @RequestParam(required = false) Integer term, 
        @RequestParam(required = false) String keyword, 
        @RequestParam(defaultValue = "1") int page, 
        @RequestParam(defaultValue = "6") int size 
    ) {
        ArchivePageResponse response = archiveService.searchArchives(
            ArchiveCategory.프로젝트, 
            track, 
            term, 
            keyword, 
            page, 
            size
        );

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "활동 사진 아카이빙 조회")
    @GetMapping("/activities")
    public ResponseEntity<ApiResponse<ArchivePageResponse>> getActivities(
        @RequestParam(required = false) Integer term, 
        @RequestParam(required = false) String keyword, 
        @RequestParam(defaultValue = "1") int page, 
        @RequestParam(defaultValue = "6") int size
    ) {
        ArchivePageResponse response = archiveService.searchArchives(
            ArchiveCategory.활동사진, 
            null, 
            term, 
            keyword, 
            page, 
            size
        );

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "기술 블로그 아카이빙 조회")
    @GetMapping("/blogs")
    public ResponseEntity<ApiResponse<ArchivePageResponse>> getBlogs(
        @RequestParam(defaultValue = "전부문") Track track, 
        @RequestParam(required = false) String keyword, 
        @RequestParam(defaultValue = "1") int page, 
        @RequestParam(defaultValue = "6") int size 
    ) {
        ArchivePageResponse response = archiveService.searchArchives(
            ArchiveCategory.기술블로그, 
            track, 
            null, 
            keyword, 
            page, 
            size
        ); 

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
