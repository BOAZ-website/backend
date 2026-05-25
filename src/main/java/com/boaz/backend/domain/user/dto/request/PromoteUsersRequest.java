package com.boaz.backend.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PromoteUsersRequest {

    @Schema(description = "승격할 유저 ID 목록", example = "[1, 2, 3, 48]")
    @NotEmpty(message = "승격할 유저 ID 목록을 입력해주세요.")
    private List<
            @NotNull(message = "유저 ID는 null일 수 없습니다.")
            @Positive(message = "유저 ID는 1 이상이어야 합니다.")
            Long
            > userIds;
}
