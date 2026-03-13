package com.boaz.backend.domain.recruitment.service;

import com.boaz.backend.domain.recruitment.dto.AnswerRequest;
import com.boaz.backend.domain.recruitment.dto.ApplicationRequest;
import com.boaz.backend.domain.recruitment.dto.ApplicationResponse;
import com.boaz.backend.domain.recruitment.dto.QuestionResponse;
import com.boaz.backend.domain.recruitment.dto.RecruitmentResponse;
import com.boaz.backend.domain.recruitment.dto.RecruitmentStatusResponse;
import com.boaz.backend.domain.recruitment.dto.SubscriptionRequest;
import com.boaz.backend.domain.recruitment.dto.SubscriptionResponse;
import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.ApplicantAnswer;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.boaz.backend.domain.recruitment.entity.QuestionCategory;
import com.boaz.backend.domain.recruitment.entity.QuestionType;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.recruitment.entity.Subscription;
import com.boaz.backend.domain.recruitment.repository.ApplicantAnswerRepository;
import com.boaz.backend.domain.recruitment.repository.ApplicantRepository;
import com.boaz.backend.domain.recruitment.repository.ApplicationQuestionRepository;
import com.boaz.backend.domain.recruitment.repository.RecruitmentRepository;
import com.boaz.backend.domain.recruitment.repository.SubscriptionRepository;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecruitmentService {

    private final RecruitmentRepository recruitmentRepository;
    private final ApplicationQuestionRepository applicationQuestionRepository;
    private final ApplicantRepository applicantRepository;
    private final ApplicantAnswerRepository applicantAnswerRepository;
    private final ObjectMapper objectMapper;
    private final SubscriptionRepository subscriptionRepository;


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
    public List<QuestionResponse> getQuestions(Long recruitmentId, Track track) {

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

        // Track → QuestionCategory 변환
        QuestionCategory trackCategory = QuestionCategory.valueOf(track.name());

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

    // 지원서 제출하기
    @Transactional
    public ApplicationResponse submitApplication(ApplicationRequest request) {

        // 공고 존재 여부 확인
        Recruitment recruitment = recruitmentRepository.findById(request.getRecruitmentId())
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

        // 모집 기간 확인
        LocalDateTime now = LocalDateTime.now();
        boolean isActive = !now.isBefore(recruitment.getStartDate())
                        && !now.isAfter(recruitment.getEndDate());
        if (!isActive) {
            throw new CustomException(ErrorCode.RECRUITMENT_CLOSED);
        }

        // 이메일 형식 검증
        if (!request.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new CustomException(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        // 전화번호 형식 검증 (하이픈 제외 숫자만)
        if (!request.getPhone().matches("^[0-9]{10,11}$")) {
            throw new CustomException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        // 생년월일 형식 검증 (YYYY-MM-DD)
        LocalDate birthDate;
        try {
            birthDate = LocalDate.parse(request.getBirthDate());
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.INVALID_BIRTH_DATE_FORMAT);
        }
        
        // 해당 공고의 질문 목록 조회
        QuestionCategory trackCategory = QuestionCategory.valueOf(request.getTrack().name());
        List<ApplicationQuestion> questions = applicationQuestionRepository
                .findByRecruitmentIdAndCategories(
                        request.getRecruitmentId(),
                        QuestionCategory.공통,
                        trackCategory
                );
        if (questions.isEmpty()) {
            throw new CustomException(ErrorCode.QUESTIONS_NOT_FOUND);
        }

        // 필수 질문 답변 여부 확인
        List<String> requiredQuestionIds = questions.stream()
                .filter(ApplicationQuestion::getIsRequired)
                .map(ApplicationQuestion::getId)
                .toList();

        List<String> answeredQuestionIds = request.getAnswers().stream()
                .map(AnswerRequest::getQuestionId)
                .toList();

        boolean allRequiredAnswered = answeredQuestionIds.containsAll(requiredQuestionIds);
        if (!allRequiredAnswered) {
            throw new CustomException(ErrorCode.ANSWER_REQUIRED);
        }

        // 중복 questionId 검증
        if (answeredQuestionIds.size() != answeredQuestionIds.stream().distinct().count()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 잘못된 questionId 검증
        List<String> validQuestionIds = questions.stream()
                .map(ApplicationQuestion::getId)
                .toList();

        for (AnswerRequest answerRequest : request.getAnswers()) {
            if (!validQuestionIds.contains(answerRequest.getQuestionId())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
        
        // 답변 형식 검증 (TEXT → String, TABLE → JSON)
        Map<String, ApplicationQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(ApplicationQuestion::getId, q -> q));

        for (AnswerRequest answerRequest : request.getAnswers()) {
            ApplicationQuestion question = questionMap.get(answerRequest.getQuestionId());
            if (question == null) continue;

            JsonNode answer = answerRequest.getAnswer();
            if (question.getIsRequired()) {
                if (answer == null || answer.isNull()
                        || (question.getType() == QuestionType.TEXT && answer.asText().trim().isEmpty())
                        || (question.getType() == QuestionType.TABLE && (!answer.isObject() || answer.size() == 0))) {
                    throw new CustomException(ErrorCode.ANSWER_REQUIRED);
                }
            }
            if (question.getType() == QuestionType.TEXT && !answer.isTextual()) {
                throw new CustomException(ErrorCode.INVALID_ANSWER_TYPE);
            }
            if (question.getType() == QuestionType.TABLE && !answer.isObject()) {
                throw new CustomException(ErrorCode.INVALID_ANSWER_TYPE);
            }
        }

        // minorDoubleMajor JSON 변환
        String minorDoubleMajorJson = null;
        if (request.getMinorDoubleMajor() != null && !request.getMinorDoubleMajor().isEmpty()) {
            try {
                minorDoubleMajorJson = objectMapper.writeValueAsString(request.getMinorDoubleMajor());
            } catch (Exception e) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }

        // 지원자 저장
        Applicant applicant = Applicant.builder()
                .recruitment(recruitment)
                .track(request.getTrack())
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .university(request.getUniversity())
                .major(request.getMajor())
                .minorDoubleMajor(minorDoubleMajorJson)
                .lastSemester(request.getLastSemester())
                .militaryStatus(request.getMilitaryStatus())
                .birthDate(birthDate)
                .graduationDate(request.getGraduationDate())
                .gradSchoolPlan(request.getGradSchoolPlan())
                .build();

        applicantRepository.save(applicant);

        // 답변 저장
        for (AnswerRequest answerRequest : request.getAnswers()) {
            ApplicationQuestion question = questionMap.get(answerRequest.getQuestionId());
            if (question == null) continue;

            JsonNode answer = answerRequest.getAnswer();
            String answerText = null;
            String answerJson = null;

            if (question.getType() == QuestionType.TEXT) {
                answerText = answer.asText();
            } else {
                try {
                    answerJson = objectMapper.writeValueAsString(answer);
                } catch (Exception e) {
                    throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
            }

            ApplicantAnswer applicantAnswer = ApplicantAnswer.builder()
                    .applicant(applicant)
                    .question(question)
                    .answerText(answerText)
                    .answerJson(answerJson)
                    .build();

            applicantAnswerRepository.save(applicantAnswer);
        }

        return ApplicationResponse.of(applicant.getId(), applicant.getCreatedAt());
    }

    // 모집 사전 알림 신청하기
    @Transactional
    public SubscriptionResponse subscribe(SubscriptionRequest request) {

        // 이메일 형식 검증
        if (!request.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new CustomException(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        // 중복 이메일 확인
        if (subscriptionRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        Subscription subscription = Subscription.builder()
                .email(request.getEmail())
                .build();

        try {
            subscriptionRepository.save(subscription);
            return SubscriptionResponse.from(subscription);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
    }
}