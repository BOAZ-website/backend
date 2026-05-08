package com.boaz.backend.domain.archive.dto.request;

import com.boaz.backend.global.common.enums.Track;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

@Getter
@NoArgsConstructor
public class ArchiveCreateRequest {

    @Schema(description = "기수", example = "8")
    @NotNull(message = "term을 입력해주세요.")
    private Integer term;

    @Schema(description = "제목", example = "AI 기반 수요 예측 프로젝트")
    @NotBlank(message = "title을 입력해주세요.")
    private String title;

    @Schema(description = "팀명", example = "팀이름")
    private String teamName;

    @Schema(description = "트랙", example = "ANALYSIS")
    @NotNull(message = "track을 입력해주세요.")
    private Track track;

    @Schema(description = "링크 JSON", example = "{\"slideshare\": \"https://slideshare.net/boaz.com\"}")
    @NotBlank(message = "links를 입력해주세요.")
    private String links;

    @Schema(description = "콘텐츠 날짜", example = "2024-07-01")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @PastOrPresent(message = "미래 날짜는 입력할 수 없습니다.")
    private LocalDate contentDate;

    @Schema(description = "상/하반기 (활동사진만 사용)", example = "26-1")
    @Pattern(regexp = "^\\d{2}-[12]$", message = "half 형식이 올바르지 않습니다. (예: 26-1)")
    private String half;

}