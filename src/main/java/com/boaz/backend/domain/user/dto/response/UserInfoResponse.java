package com.boaz.backend.domain.user.dto.response;

import com.boaz.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserInfoResponse {

    private String nickname;

    public static UserInfoResponse from(User user) {
        return UserInfoResponse.builder()
                .nickname(user.getNickname())
                .build();
    }
}
