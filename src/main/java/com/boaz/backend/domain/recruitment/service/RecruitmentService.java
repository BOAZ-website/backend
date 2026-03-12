package com.boaz.backend.domain.recruitment.service;

import com.boaz.backend.domain.recruitment.dto.QuestionResponse;
import com.boaz.backend.domain.recruitment.dto.RecruitmentResponse;
import com.boaz.backend.domain.recruitment.dto.RecruitmentStatusResponse;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.boaz.backend.domain.recruitment.entity.QuestionCategory;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.recruitment.repository.ApplicationQuestionRepository;
import com.boaz.backend.domain.recruitment.repository.RecruitmentRepository;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecruitmentService {

    private final RecruitmentRepository recruitmentRepository;
    private final ApplicationQuestionRepository applicationQuestionRepository;


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
        boolean isActive = !now.isBefore(recruitment.getStartDate()) 
                        && !now.isAfter(recruitment.getEndDate());

        if (!isActive) {
            return RecruitmentResponse.inactive();
        }
        return RecruitmentResponse.from(recruitment);
    }

    // 지원서 질문 조회하기
    public List<QuestionResponse> getQuestions(Long recruitmentId, String trackParam) {

        // 공고 존재 여부 확인
        Recruitment recruitment = recruitmentRepository.findById(recruitmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

        // 모집 중 여부 확인
        LocalDateTime now = LocalDateTime.now();
        boolean isActive = !now.isBefore(recruitment.getStartDate()) 
                        && !now.isAfter(recruitment.getEndDate());
        if (!isActive) {
            throw new CustomException(ErrorCode.RECRUITMENT_NOT_AVAILABLE);
        }

        // track 유효성 검사
        QuestionCategory trackCategory;
        try {
            trackCategory = QuestionCategory.valueOf(trackParam);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TRACK_NAME);
        }

        // 공통 + 해당 부문 질문 조회
        List<ApplicationQuestion> questions = applicationQuestionRepository
                .findByRecruitmentIdAndCategories(recruitmentId, QuestionCategory.공통, trackCategory);

        if (questions.isEmpty()) {
            throw new CustomException(ErrorCode.QUESTIONS_NOT_FOUND);
        }

        return questions.stream()
                .map(QuestionResponse::from)
                .toList();
    }
}