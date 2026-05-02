package com.boaz.backend.domain.archive.dto.request;

import com.boaz.backend.global.common.enums.Track;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

@Getter
@NoArgsConstructor
public class ArchiveCreateRequest {
    
    @Schema(description = "기수", example = "8", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer term;

    @Schema(description = "제목", example = "AI 기반 수요 예측 프로젝트", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "팀명", example = "팀이름")
    private String teamName;

    @Schema(description = "트랙", example = "ANALYSIS", requiredMode = Schema.RequiredMode.REQUIRED)
    private Track track;

    @Schema(description = "링크 JSON", example = "{\"slideshare\": \"https://slideshare.net/boaz.com\"}", requiredMode = Schema.RequiredMode.REQUIRED)
    private String links;

    @Schema(description = "콘텐츠 날짜", example = "2024-07-01")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate contentDate;

    @Schema(description = "상/하반기 (활동사진만 사용)", example = "26-1")
    private String half;

}