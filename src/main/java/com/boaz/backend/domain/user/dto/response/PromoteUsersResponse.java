package com.boaz.backend.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Getter
public class PromoteUsersResponse {

    @Schema(description = "승격 실패한 유저 ID 목록 및 사유")
    private final List<FailedUserInfo> failedUserIds;

    private PromoteUsersResponse(List<FailedUserInfo> failedUserIds) {
        this.failedUserIds = failedUserIds;
    }

    public static PromoteUsersResponse of(List<FailedUserInfo> failedUserIds) {
        return new PromoteUsersResponse(failedUserIds);
    }

    @Getter
    public static class FailedUserInfo {

        @Schema(description = "유저 ID", example = "5")
        private final Long userId;

        @Schema(description = "에러 코드", example = "USER_NOT_FOUND")
        private final String errorCode;

        public FailedUserInfo(Long userId, String errorCode) {
            this.userId = userId;
            this.errorCode = errorCode;
        }
    }
}
