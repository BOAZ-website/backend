package com.boaz.backend.domain.user.dto.response;

import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.global.common.enums.MemberType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserInfoResponse {

    private String nickname;
    private MemberType memberType;

    public static UserInfoResponse from(User user) {
        return UserInfoResponse.builder()
                .nickname(user.getNickname())
                .memberType(user.getMemberType())
                .build();
    }
}
