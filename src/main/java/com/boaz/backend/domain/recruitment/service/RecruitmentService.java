package com.boaz.backend.domain.recruitment.service;

import com.boaz.backend.domain.recruitment.dto.RecruitmentResponse;
import com.boaz.backend.domain.recruitment.dto.RecruitmentStatusResponse;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.recruitment.repository.RecruitmentRepository;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecruitmentService {

    private final RecruitmentRepository recruitmentRepository;

    // 모집 중 여부 조회
    public RecruitmentStatusResponse getRecruitmentStatus() {
        boolean isActive = recruitmentRepository
                .findActiveRecruitment(LocalDateTime.now())
                .isPresent();
        return RecruitmentStatusResponse.of(isActive);
    }

    // 기수별 모집 공고 조회
    public RecruitmentResponse getRecruitment(Integer term) {
        Recruitment recruitment = recruitmentRepository.findByTerm(term)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));
        
        LocalDateTime now = LocalDateTime.now();
        boolean isActive = now.isAfter(recruitment.getStartDate()) 
                        && now.isBefore(recruitment.getEndDate());

        if (!isActive) {
            return RecruitmentResponse.inactive();
        }
        return RecruitmentResponse.from(recruitment);
    }
}