package com.boaz.backend.domain.user.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PromoteUsersRequest {

    @NotEmpty(message = "승격할 유저 ID 목록을 입력해주세요.")
    private List<Long> userIds;
}
