package com.boaz.backend.domain.curriculum.controller;

import com.boaz.backend.domain.curriculum.dto.request.CurriculumCreateRequest;
import com.boaz.backend.domain.curriculum.dto.request.CurriculumUpdateRequest;
import com.boaz.backend.domain.curriculum.dto.response.CurriculumResponse;
import com.boaz.backend.domain.curriculum.service.CurriculumService;
import com.boaz.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "[Admin] Curriculum", description = "Admin 전용 커리큘럼 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/curriculums")
public class CurriculumAdminController {

    private final CurriculumService curriculumService;

    @Operation(summary = "커리큘럼 등록", description = "부문별 커리큘럼을 등록합니다. 동일 부문 중복 등록 불가.")
    @PostMapping
    public ResponseEntity<ApiResponse<CurriculumResponse>> createCurriculum(
            @RequestBody @Valid CurriculumCreateRequest request) {
        CurriculumResponse response = curriculumService.createCurriculum(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "커리큘럼 수정", description = "커리큘럼 단계를 전체 교체합니다. 부문 변경 불가.")
    @PutMapping("/{curriculumId}")
    public ResponseEntity<ApiResponse<CurriculumResponse>> updateCurriculum(
            @PathVariable Long curriculumId,
            @RequestBody @Valid CurriculumUpdateRequest request) {
        CurriculumResponse response = curriculumService.updateCurriculum(curriculumId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "커리큘럼 삭제", description = "커리큘럼을 삭제합니다.")
    @DeleteMapping("/{curriculumId}")
    public ResponseEntity<ApiResponse<Void>> deleteCurriculum(
            @PathVariable Long curriculumId) {
        curriculumService.deleteCurriculum(curriculumId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
