package com.boaz.backend.domain.archive.dto.response;

import com.boaz.backend.domain.archive.entity.Archive;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.extern.slf4j.Slf4j;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;


@Getter
@Builder
@Slf4j
public class ArchiveItemResponse {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Schema(description = "아카이브 ID", example = "1")
    private Long id;

    @Schema(description = "기수", example = "26")
    private Integer term;

    @Schema(description  = "제목", example = "실시간 추천 시스템 구축 프로젝트")
    private String title;

    @Schema(description = "팀명", example = "팀 보아즈")
    private String teamName;

    @Schema(description = "트랙", example = "ENGINEERING")
    private Track track;

    @Schema(description = "이미지 URL", example = "https://boaz-archiving.s3.ap-northeast-2.amazonaws.com/projects/sample.png")
    private String imageUrl;

    @Schema(description = "관련 링크(JSON)", example = "{\"slideshare\": \"https://www.slideshare.net/example\"}")
    private JsonNode links;

    @Schema(description = "콘텐츠 날짜", example = "2026-04-17")
    private LocalDate contentDate;

    // DB에서 가져온 Entity 객체를 API 응답용 DTO 객체로 변환 
    public static ArchiveItemResponse from(Archive archive) {
        return ArchiveItemResponse.builder()
            .id(archive.getId())
            .term(archive.getTerm())
            .title(archive.getTitle())
            .teamName(archive.getTeamName())
            .track(archive.getTrack())
            .imageUrl(archive.getImageUrl())
            .links(parseLinks(archive.getLinks()))
            .contentDate(archive.getContentDate())
            .build();
    }

    // links JSON 문자열을 JsonNode로 변환
    private static JsonNode parseLinks(String linksJson) {

        // null 또는 공백이면 null 반환
        if (linksJson == null || linksJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(linksJson);

        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}