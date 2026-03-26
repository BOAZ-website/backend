package com.boaz.backend.domain.archive.service;

import com.boaz.backend.domain.archive.dto.ArchiveItemResponse;
import com.boaz.backend.domain.archive.dto.ArchivePageResponse;
import com.boaz.backend.domain.archive.entity.Archive;
import com.boaz.backend.domain.archive.entity.ArchiveCategory;
import com.boaz.backend.domain.archive.repository.ArchiveRepository;
import com.boaz.backend.global.common.enums.Track;

import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveService {

    private static final int MAX_PAGE_SIZE = 100; 
    private final ArchiveRepository archiveRepository;

    // 컨트롤러가 넘겨준 값들을 서비스가 받는 부분 
    public ArchivePageResponse searchArchives(
        ArchiveCategory category, 
        Track track, 
        Integer term, 
        String keyword, 
        int page, 
        int size 
    ) {
        validatePagination(page, size);

        if (term != null && term < 0) {
            throw new CustomException(ErrorCode.INVALID_PARAMETER_TYPE);
        }

        int zeroBasedPage = page - 1;   // 페이지 0기반으로 변환 
     
        String normalizedKeyword = normalizeKeyword(keyword);

        // 전부문이면 track 조건을 아예 걸지 않도록 null 처리
        Track effectiveTrack = (track == Track.전부문) ? null : track;

        // Repository에 검색 조건과 페이징 정보를 전달해 DB 조회 수행 
        Page<Archive> archives = archiveRepository.searchArchives(
            category, 
            effectiveTrack, 
            term, 
            normalizedKeyword, 
            PageRequest.of(zeroBasedPage, size)
        );

        // 조회된 DB 엔티티를 프론트 응답용 DTO로 변환
        // Page.map()을 사용하면 페이지 정보는 유지하면서 content만 변환됨. 
        Page<ArchiveItemResponse> itemPage = 
            archives.map(ArchiveItemResponse::from);

        // 페이지 메타데이터 + 게시글 목록을 포함한 응답 DTO로 변환  
        return ArchivePageResponse.from(itemPage);   
    }

    // 페이지 값 검증
    private void validatePagination(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_PAGINATION);
        }
    }

    // 검색어 전처리 메서드
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}