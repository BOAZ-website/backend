package com.boaz.backend.domain.user.dto.response;

import lombok.Getter;

import java.util.List;

@Getter
public class PromoteUsersResponse {

    private final List<FailedUserInfo> failedUserIds;

    private PromoteUsersResponse(List<FailedUserInfo> failedUserIds) {
        this.failedUserIds = failedUserIds;
    }

    public static PromoteUsersResponse of(List<FailedUserInfo> failedUserIds) {
        return new PromoteUsersResponse(failedUserIds);
    }

    @Getter
    public static class FailedUserInfo {
        private final Long userId;
        private final String errorCode;

        public FailedUserInfo(Long userId, String errorCode) {
            this.userId = userId;
            this.errorCode = errorCode;
        }
    }
}
