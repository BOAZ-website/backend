package com.boaz.backend.domain.archive.dto;

import com.boaz.backend.domain.archive.entity.Archive;
import com.boaz.backend.global.common.enums.Track;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.time.LocalDate;


@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class ArchiveItemResponse {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Long id;

    private Integer term;   // 기술블로그일 경우 null이면 JSON에서 자동 제외

    private String title;

    private String teamName;

    private Track track;

    private String imageUrl;

    private Map<String, String> links;

    private LocalDate contentDate;

    // DB에서 가져온 Entity 객체를 API 응답용 DTO 객체로 변환 
    public static ArchiveItemResponse fromEntity(Archive archive) {
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

    // links JSON 문자열을 Map<String, String> 으로 변환
    private static Map<String, String> parseLinks(String linksJson) {

        // null 또는 공백이면 null 반환
        if (linksJson == null || linksJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(
                linksJson, 
                new TypeReference<Map<String, String>>() {}
            );
        } catch (Exception e) {
            // 파싱 실패 시 로그만 남기고 null 반환
            log.warn("links JSON 파싱에 실패했습니다. 값: {}", linksJson, e);
            return null;
        }
    }


}