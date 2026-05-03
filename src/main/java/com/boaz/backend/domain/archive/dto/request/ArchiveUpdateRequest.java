package com.boaz.backend.domain.archive.dto.request;

import com.boaz.backend.global.common.enums.Track;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

@Getter
@NoArgsConstructor
public class ArchiveUpdateRequest {

    @Schema(description = "기수", example = "8")
    private Integer term;

    @Schema(description = "제목", example = "LLM 기반 수요 예측 프로젝트")
    private String title;

    @Schema(description = "팀명", example = "보보보아아즈")
    private String teamName;

    @Schema(description = "트랙", example = "ANALYSIS")
    private Track track;

    @Schema(description = "링크 JSON", example = "{\"slideshare\": \"https://slideshare.net/bbbaaz.com\"}")
    private String links;

    @Schema(description = "콘텐츠 날짜", example = "2024-07-05")
    private JsonNullable<LocalDate> contentDate = JsonNullable.undefined();

    @Schema(description = "상/하반기 (활동사진만 사용)", example = "26-1")
    private String half;
}
