package com.boaz.backend.domain.user.controller;

import com.boaz.backend.domain.user.dto.request.PromoteUsersRequest;
import com.boaz.backend.domain.user.dto.response.PromoteUsersResponse;
import com.boaz.backend.domain.user.service.UserAdminService;
import com.boaz.backend.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[Admin] User", description = "Admin 전용 유저 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    @Operation(
            summary = "합격자 승격",
            description = "합격자 userIds를 받아 memberType을 MEMBER로 변경하고 지원서 개인정보를 User에 복사한다. " +
                          "비즈니스 오류가 발생한 userId는 skip하고 failedUserIds에 포함하여 반환한다."
    )
    @PatchMapping("/promote")
    public ResponseEntity<ApiResponse<PromoteUsersResponse>> promoteUsers(
            @RequestBody @Valid PromoteUsersRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userAdminService.bulkPromote(request.getUserIds())));
    }
}
