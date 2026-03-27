package com.boaz.backend.domain.curriculum.controller;

import com.boaz.backend.domain.curriculum.dto.CurriculumResponse;
import com.boaz.backend.domain.curriculum.service.CurriculumService;
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

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/curriculums")
@Tag(name = "Curriculum", description = "커리큘럼 API")
public class CurriculumController {

    private final CurriculumService curriculumService;

    @GetMapping
    @Operation(summary = "커리큘럼 전체 조회", description = "트랙 필터링 가능. 미입력 시 전체 3개 트랙 반환.")
    public ResponseEntity<ApiResponse<List<CurriculumResponse>>> getCurriculums(
            @RequestParam(required = false) Track track) {
        return ResponseEntity.ok(ApiResponse.ok(curriculumService.getCurriculums(track)));
    }
}
