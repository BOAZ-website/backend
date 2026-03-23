package com.boaz.backend.domain.archive.dto;

import com.boaz.backend.domain.archive.entity.Archive;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
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
@Slf4j
public class ArchiveItemResponse {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Long id;
    private Integer term;
    private String title;
    private String teamName;
    private Track track;
    private String imageUrl;
    private Map<String, String> links;
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
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}