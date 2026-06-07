package com.boaz.backend.domain.recruitment.service;

import com.boaz.backend.domain.recruitment.dto.request.AnswerRequest;
import com.boaz.backend.domain.recruitment.dto.request.ApplicationRequest;
import com.boaz.backend.domain.recruitment.dto.request.DraftApplicationRequest;
import com.boaz.backend.domain.recruitment.dto.request.QuestionItemRequest;
import com.boaz.backend.domain.recruitment.dto.request.QuestionsCreateRequest;
import com.boaz.backend.domain.recruitment.dto.request.QuestionUpdateRequest;
import com.boaz.backend.domain.recruitment.dto.request.RecruitmentCreateRequest;
import com.boaz.backend.domain.recruitment.dto.request.RecruitmentUpdateRequest;
import com.boaz.backend.domain.recruitment.dto.request.SubscriptionRequest;
import com.boaz.backend.domain.recruitment.dto.response.ApplicationResponse;
import com.boaz.backend.domain.recruitment.dto.response.DeadlineResponse;
import com.boaz.backend.domain.recruitment.dto.response.DraftApplicationResponse;
import com.boaz.backend.domain.recruitment.dto.response.MyApplicationResponse;
import com.boaz.backend.domain.recruitment.dto.response.QuestionIdResponse;
import com.boaz.backend.domain.recruitment.dto.response.QuestionIdsResponse;
import com.boaz.backend.domain.recruitment.dto.response.QuestionResponse;
import com.boaz.backend.domain.recruitment.dto.response.RecruitmentIdResponse;
import com.boaz.backend.domain.recruitment.dto.response.RecruitmentResponse;
import com.boaz.backend.domain.recruitment.dto.response.RecruitmentStatusResponse;
import com.boaz.backend.domain.recruitment.dto.response.SubscriptionResponse;
import org.openapitools.jackson.nullable.JsonNullable;
import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.ApplicantAnswer;
import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import com.boaz.backend.domain.recruitment.entity.Recruitment;
import com.boaz.backend.domain.recruitment.entity.Subscription;
import com.boaz.backend.domain.recruitment.repository.ApplicantAnswerRepository;
import com.boaz.backend.domain.recruitment.repository.ApplicantRepository;
import com.boaz.backend.domain.recruitment.repository.ApplicationQuestionRepository;
import com.boaz.backend.domain.recruitment.repository.RecruitmentRepository;
import com.boaz.backend.domain.recruitment.repository.SubscriptionRepository;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.repository.UserRepository;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.util.S3Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecruitmentService {

    private final RecruitmentRepository recruitmentRepository;
    private final ApplicationQuestionRepository applicationQuestionRepository;
    private final ApplicantRepository applicantRepository;
    private final ApplicantAnswerRepository applicantAnswerRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final SubscriptionRepository subscriptionRepository;
    private final CsvService csvService;
    private final Clock clock;

    @Value("${spring.cloud.aws.s3.recruitment-bucket}")
    private String recruitmentBucket;

    private final S3Service s3Service;

    // 모집 기간 판정용 현재 시각 (초 단위 버림)
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    // 모집 중 여부 조회
    public RecruitmentStatusResponse getRecruitmentStatus() {
        LocalDateTime now = now();
        return recruitmentRepository.findCurrentOrUpcoming(now)
                .map(r -> RecruitmentStatusResponse.of(r.isActive(now), r.getTerm()))
                .orElse(RecruitmentStatusResponse.of(false, null));
    }

    // 모집 공고 마감 일시 조회
    public DeadlineResponse getDeadline() {
        LocalDateTime now = now();
        Recruitment recruitment = recruitmentRepository.findActiveRecruitment(now)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));
        return DeadlineResponse.from(recruitment);
    }

    // 기수별 모집 공고 조회
    public RecruitmentResponse getRecruitment(Integer term) {
        Recruitment recruitment = recruitmentRepository.findByTerm(term)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

        return RecruitmentResponse.from(recruitment, now());
    }

    // 지원서 질문 조회하기
    public List<QuestionResponse> getQuestions(Long recruitmentId, Track track) {

        // 공고 존재 여부 확인
        Recruitment recruitment = recruitmentRepository.findById(recruitmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

        // 모집 중 여부 확인
        if (!recruitment.isActive(now())) {
            throw new CustomException(ErrorCode.RECRUITMENT_NOT_AVAILABLE);
        }

        // 부문 검증
        track.validateNotAll();

        // Track → ApplicationQuestion.Category 변환
        ApplicationQuestion.Category trackCategory = toQuestionCategory(track);

        // 공통 + 해당 부문 질문 조회
        List<ApplicationQuestion> questions = applicationQuestionRepository
                .findByRecruitmentIdAndCategories(recruitmentId, ApplicationQuestion.Category.COMMON, trackCategory);

        if (questions.isEmpty()) {
            throw new CustomException(ErrorCode.QUESTIONS_NOT_FOUND);
        }

        return questions.stream()
            .map(question -> {
                String content = question.getContent() != null
                        ? question.getContent().replace("{Track}", track.getDisplayName())
                        : null;
                return QuestionResponse.from(question, content);
            })
            .toList();
    }

    // 지원서 제출하기
    @Transactional
    public ApplicationResponse submitApplication(Long userId, Long recruitmentId, ApplicationRequest request) {

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 공고 존재 여부 확인
        Recruitment recruitment = recruitmentRepository.findById(recruitmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

        // 모집 기간 확인
        if (!recruitment.isActive(now())) {
            throw new CustomException(ErrorCode.RECRUITMENT_CLOSED);
        }

        // 이미 제출된 지원서 확인
        Optional<Applicant> existing = applicantRepository.findByRecruitmentIdAndUserId(recruitmentId, userId);
        if (existing.isPresent() && existing.get().getStatus() == Applicant.ApplicantStatus.SUBMITTED) {
            throw new CustomException(ErrorCode.ALREADY_SUBMITTED);
        }

        // 부문 검증
        request.getTrack().validateNotAll();

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
        ApplicationQuestion.Category trackCategory = toQuestionCategory(request.getTrack());
        List<ApplicationQuestion> questions = applicationQuestionRepository
                .findByRecruitmentIdAndCategories(
                        recruitmentId,
                        ApplicationQuestion.Category.COMMON,
                        trackCategory
                );
        if (questions.isEmpty()) {
            throw new CustomException(ErrorCode.QUESTIONS_NOT_FOUND);
        }

        // 필수 질문 답변 여부 확인
        List<Long> requiredQuestionIds = questions.stream()
                .filter(ApplicationQuestion::getIsRequired)
                .map(ApplicationQuestion::getId)
                .toList();

        List<Long> answeredQuestionIds = request.getAnswers().stream()
                .map(AnswerRequest::getQuestionId)
                .toList();

        if (!answeredQuestionIds.containsAll(requiredQuestionIds)) {
            throw new CustomException(ErrorCode.ANSWER_REQUIRED);
        }

        // 중복 questionId 검증
        if (answeredQuestionIds.size() != answeredQuestionIds.stream().distinct().count()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 유효한 questionId 목록
        List<Long> validQuestionIds = questions.stream()
                .map(ApplicationQuestion::getId)
                .toList();

        // 잘못된 questionId 검증
        for (AnswerRequest answerRequest : request.getAnswers()) {
            if (!validQuestionIds.contains(answerRequest.getQuestionId())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }

        // 답변 형식 검증 (TEXT → String, TABLE → JSON)
        Map<Long, ApplicationQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(ApplicationQuestion::getId, q -> q));

        for (AnswerRequest answerRequest : request.getAnswers()) {
            ApplicationQuestion question = questionMap.get(answerRequest.getQuestionId());
            if (question == null) continue;

            JsonNode answer = answerRequest.getAnswer();
            if (answer == null) {
                throw new CustomException(
                        question.getIsRequired() ? ErrorCode.ANSWER_REQUIRED : ErrorCode.INVALID_ANSWER_TYPE
                );
            }

            if (question.getType() == ApplicationQuestion.Type.TEXT) {
                if (!answer.isTextual()) throw new CustomException(ErrorCode.INVALID_ANSWER_TYPE);
                if (question.getIsRequired() && answer.asText().trim().isEmpty()) {
                    throw new CustomException(ErrorCode.ANSWER_REQUIRED);
                }
            } else {
                if (!answer.isObject()) throw new CustomException(ErrorCode.INVALID_ANSWER_TYPE);
                boolean multiple = isMultiple(question);
                if (multiple) {
                    answer.fields().forEachRemaining(entry -> {
                        if (!entry.getValue().isArray()) throw new CustomException(ErrorCode.INVALID_ANSWER_TYPE);
                        entry.getValue().forEach(elem -> {
                            if (!elem.isTextual()) throw new CustomException(ErrorCode.INVALID_ANSWER_TYPE);
                        });
                    });
                    if (question.getIsRequired()) {
                        boolean hasSelection = false;
                        for (JsonNode val : answer) {
                            if (val.isArray() && val.size() > 0) { hasSelection = true; break; }
                        }
                        if (!hasSelection) throw new CustomException(ErrorCode.ANSWER_REQUIRED);
                    }
                } else {
                    answer.fields().forEachRemaining(entry -> {
                        if (!entry.getValue().isTextual()) throw new CustomException(ErrorCode.INVALID_ANSWER_TYPE);
                    });
                    if (question.getIsRequired() && answer.size() == 0) {
                        throw new CustomException(ErrorCode.ANSWER_REQUIRED);
                    }
                }
            }
        }

        // minorDoubleMajor JSON 변환
        String minorDoubleMajorJson = serializeList(request.getMinorDoubleMajor());

        // DRAFT 존재 시 덮어쓰기, 없으면 신규 생성
        Applicant applicant;
        if (existing.isPresent()) {
            // DRAFT → SUBMITTED 전환 (overwrite: 전체 교체)
            applicant = existing.get();
            applicantAnswerRepository.deleteByApplicantId(applicant.getId());
            applicant.overwrite(
                    request.getTrack(), request.getName(), request.getEmail(), request.getPhone(),
                    request.getUniversity(), request.getMajor(), minorDoubleMajorJson,
                    request.getLastSemester(), request.getMilitaryStatus(), birthDate,
                    request.getGraduationDate(), request.getGradSchoolPlan()
            );
        } else {
            applicant = Applicant.builder()
                    .recruitment(recruitment)
                    .user(user)
                    .status(Applicant.ApplicantStatus.SUBMITTED)
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
            applicant.markSubmitted();
        }

        // 답변 저장
        for (AnswerRequest answerRequest : request.getAnswers()) {
            ApplicationQuestion question = questionMap.get(answerRequest.getQuestionId());
            if (question == null) continue;

            JsonNode answer = answerRequest.getAnswer();
            String answerText = null;
            String answerJson = null;

            if (question.getType() == ApplicationQuestion.Type.TEXT) {
                answerText = answer.asText();
            } else {
                try {
                    JsonNode toSave = isMultiple(question) ? dedupeMultipleAnswer(answer) : answer;
                    answerJson = objectMapper.writeValueAsString(toSave);
                } catch (Exception e) {
                    throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
            }

            applicantAnswerRepository.save(ApplicantAnswer.builder()
                    .applicant(applicant)
                    .question(question)
                    .answerText(answerText)
                    .answerJson(answerJson)
                    .build());
        }

        return ApplicationResponse.of(applicant.getId(), applicant.getSubmittedAt());
    }

    // 지원서 임시저장하기
    @Transactional
    public DraftApplicationResponse saveDraft(Long userId, Long recruitmentId, DraftApplicationRequest request) {

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 공고 존재 여부 확인
        Recruitment recruitment = recruitmentRepository.findById(recruitmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

        // 모집 기간 확인
        if (!recruitment.isActive(now())) {
            throw new CustomException(ErrorCode.RECRUITMENT_CLOSED);
        }

        // 기존 지원서 조회
        Optional<Applicant> existing = applicantRepository.findByRecruitmentIdAndUserId(recruitmentId, userId);
        if (existing.isPresent() && existing.get().getStatus() == Applicant.ApplicantStatus.SUBMITTED) {
            throw new CustomException(ErrorCode.APPLICATION_ALREADY_SUBMITTED);
        }

        // minorDoubleMajor JSON 변환 (null이면 null 유지)
        String minorDoubleMajorJson = null;
        if (request.getMinorDoubleMajor() != null) {
            minorDoubleMajorJson = serializeList(request.getMinorDoubleMajor());
        }

        // 생년월일 파싱 (null이면 null 유지)
        LocalDate birthDate = null;
        if (request.getBirthDate() != null) {
            try {
                birthDate = LocalDate.parse(request.getBirthDate());
            } catch (DateTimeParseException e) {
                throw new CustomException(ErrorCode.INVALID_BIRTH_DATE_FORMAT);
            }
        }

        Applicant applicant;
        if (existing.isPresent()) {
            // DRAFT 존재 → 부분 업데이트
            applicant = existing.get();
            applicant.update(
                    request.getTrack(), request.getName(), request.getEmail(), request.getPhone(),
                    request.getUniversity(), request.getMajor(), minorDoubleMajorJson,
                    request.getLastSemester(), request.getMilitaryStatus(), birthDate,
                    request.getGraduationDate(), request.getGradSchoolPlan()
            );
        } else {
            // 신규 생성
            applicant = Applicant.builder()
                    .recruitment(recruitment)
                    .user(user)
                    .status(Applicant.ApplicantStatus.DRAFT)
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
        }

        // answers 처리: null이면 유지, 빈 배열이면 전체 삭제, 값이 있으면 교체
        if (request.getAnswers() != null) {
            applicantAnswerRepository.deleteByApplicantId(applicant.getId());

            if (!request.getAnswers().isEmpty()) {
                // 해당 공고에 등록된 유효한 questionId 로드
                List<ApplicationQuestion> allQuestions =
                        applicationQuestionRepository.findByRecruitmentIdOrderByOrderNumAsc(recruitmentId);
                Set<Long> validQuestionIds = allQuestions.stream()
                        .map(ApplicationQuestion::getId)
                        .collect(Collectors.toSet());
                Map<Long, ApplicationQuestion> questionMap = allQuestions.stream()
                        .collect(Collectors.toMap(ApplicationQuestion::getId, q -> q));

                for (AnswerRequest answerRequest : request.getAnswers()) {
                    // 알 수 없는 questionId는 skip
                    if (!validQuestionIds.contains(answerRequest.getQuestionId())) continue;

                    ApplicationQuestion question = questionMap.get(answerRequest.getQuestionId());
                    JsonNode answer = answerRequest.getAnswer();

                    String answerText = null;
                    String answerJson = null;
                    if (question.getType() == ApplicationQuestion.Type.TEXT) {
                        if (answer != null && !answer.isTextual()) {
                            throw new CustomException(ErrorCode.INVALID_ANSWER_TYPE);
                        }
                        answerText = answer != null ? answer.asText() : null;
                    } else {
                        if (answer != null && !answer.isObject()) {
                            throw new CustomException(ErrorCode.INVALID_ANSWER_TYPE);
                        }
                        if (answer != null) {
                            boolean multiple = isMultiple(question);
                            if (multiple) {
                                answer.fields().forEachRemaining(entry -> {
                                    if (!entry.getValue().isArray()) throw new CustomException(ErrorCode.INVALID_ANSWER_TYPE);
                                    entry.getValue().forEach(elem -> {
                                        if (!elem.isTextual()) throw new CustomException(ErrorCode.INVALID_ANSWER_TYPE);
                                    });
                                });
                            } else {
                                answer.fields().forEachRemaining(entry -> {
                                    if (!entry.getValue().isTextual()) throw new CustomException(ErrorCode.INVALID_ANSWER_TYPE);
                                });
                            }
                        }
                        try {
                            JsonNode toSave = (answer != null && isMultiple(question))
                                    ? dedupeMultipleAnswer(answer) : answer;
                            answerJson = toSave != null ? objectMapper.writeValueAsString(toSave) : null;
                        } catch (Exception e) {
                            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
                        }
                    }

                    applicantAnswerRepository.save(ApplicantAnswer.builder()
                            .applicant(applicant)
                            .question(question)
                            .answerText(answerText)
                            .answerJson(answerJson)
                            .build());
                }
            }
        }

        return DraftApplicationResponse.of(applicant.getId());
    }

    // 내 지원서 조회하기 (DRAFT만 허용)
    public MyApplicationResponse getMyApplication(Long userId, Long recruitmentId) {

        // 공고 존재 여부 확인
        recruitmentRepository.findById(recruitmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

        // 지원서 조회
        Applicant applicant = applicantRepository.findByRecruitmentIdAndUserId(recruitmentId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        // SUBMITTED 상태 접근 불가
        if (applicant.getStatus() == Applicant.ApplicantStatus.SUBMITTED) {
            throw new CustomException(ErrorCode.APPLICATION_ALREADY_SUBMITTED);
        }

        // minorDoubleMajor 파싱
        List<String> minorDoubleMajor = null;
        if (applicant.getMinorDoubleMajor() != null) {
            try {
                minorDoubleMajor = objectMapper.readValue(
                        applicant.getMinorDoubleMajor(),
                        new TypeReference<List<String>>() {}
                );
            } catch (Exception e) {
                minorDoubleMajor = Collections.emptyList();
            }
        }

        // 답변 조회 및 변환
        List<ApplicantAnswer> rawAnswers =
                applicantAnswerRepository.findByApplicantIds(List.of(applicant.getId()));

        List<MyApplicationResponse.AnswerItemResponse> answers = rawAnswers.stream()
                .map(aa -> {
                    JsonNode answerNode;
                    if (aa.getAnswerText() != null) {
                        answerNode = TextNode.valueOf(aa.getAnswerText());
                    } else {
                        try {
                            answerNode = objectMapper.readTree(aa.getAnswerJson());
                        } catch (Exception e) {
                            answerNode = objectMapper.nullNode();
                        }
                    }
                    return new MyApplicationResponse.AnswerItemResponse(
                            aa.getQuestion().getId(), answerNode
                    );
                })
                .toList();

        return MyApplicationResponse.of(applicant, minorDoubleMajor, answers);
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
            subscriptionRepository.saveAndFlush(subscription);
            return SubscriptionResponse.from(subscription);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
    }


    // ========================
    // Admin 전용 메서드
    // ========================

    // 지원서 파일 다운로드
    public void downloadApplications(Integer term) {

        // 공고 존재 여부 확인
        Recruitment recruitment = recruitmentRepository.findByTerm(term)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // 부문별 CSV 생성 및 S3 업로드
        for (Track track : List.of(Track.VISUALIZATION, Track.ANALYSIS, Track.ENGINEERING)) {

            // 해당 부문 제출된 지원자 조회 (submittedAt 오름차순 + user JOIN FETCH)
            List<Applicant> applicants = applicantRepository
                    .findSubmittedByRecruitmentIdAndTrackOrderBySubmittedAt(
                            recruitment.getId(), track, Applicant.ApplicantStatus.SUBMITTED);

            // 공통 + 해당 부문 질문 조회
            List<ApplicationQuestion> questions = applicationQuestionRepository
                    .findByRecruitmentIdAndCategories(
                            recruitment.getId(),
                            ApplicationQuestion.Category.COMMON,
                            toQuestionCategory(track)
                    );

            // 지원자 답변 조회
            List<Long> applicantIds = applicants.stream()
                    .map(Applicant::getId)
                    .toList();

            List<ApplicantAnswer> answers = applicantIds.isEmpty()
                    ? Collections.emptyList()
                    : applicantAnswerRepository.findByApplicantIds(applicantIds);

            // CSV 생성
            byte[] csv;
            try {
                csv = csvService.generateCsv(applicants, questions, answers);
            } catch (IOException e) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            // S3 업로드
            String key = String.format("%d/applicants_%s_%s.csv",
                    term, track.name(), timestamp);
            s3Service.uploadCsv(recruitmentBucket, key, csv);
        }
    }

    private ApplicationQuestion.Category toQuestionCategory(Track track) {
        return switch (track) {
            case ANALYSIS -> ApplicationQuestion.Category.ANALYSIS;
            case VISUALIZATION -> ApplicationQuestion.Category.VISUALIZATION;
            case ENGINEERING -> ApplicationQuestion.Category.ENGINEERING;
            default -> throw new CustomException(ErrorCode.INVALID_PARAMETER_TYPE);
        };
    }

    // 모든 모집 공고 조회 (term 내림차순)
    public List<RecruitmentResponse> getAllRecruitments() {
        LocalDateTime now = now();
        return recruitmentRepository.findAllByOrderByTermDesc().stream()
                .map(r -> RecruitmentResponse.from(r, now))
                .toList();
    }

    // 모집 공고 등록
    @Transactional
    public RecruitmentIdResponse createRecruitment(RecruitmentCreateRequest request) {
        if (recruitmentRepository.existsByTerm(request.getTerm())) {
            throw new CustomException(ErrorCode.DUPLICATE_TERM);
        }

        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String scheduleJson;
        try {
            scheduleJson = objectMapper.writeValueAsString(request.getSchedule());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        Recruitment recruitment = Recruitment.create(
                request.getTerm(),
                request.getStartDate(),
                request.getEndDate(),
                scheduleJson,
                request.getBrochureUrl()
        );

        recruitmentRepository.save(recruitment);

        return RecruitmentIdResponse.of(recruitment.getId());
    }

    // 모집 공고 수정
    @Transactional
    public RecruitmentIdResponse updateRecruitment(Long id, RecruitmentUpdateRequest request) {
        Recruitment recruitment = recruitmentRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

        if (request.getTerm() != null &&
                recruitmentRepository.existsByTermAndIdNot(request.getTerm(), id)) {
            throw new CustomException(ErrorCode.DUPLICATE_TERM);
        }

        LocalDateTime resolvedStart = request.getStartDate() != null ? request.getStartDate() : recruitment.getStartDate();
        LocalDateTime resolvedEnd = request.getEndDate() != null ? request.getEndDate() : recruitment.getEndDate();
        if (!resolvedEnd.isAfter(resolvedStart)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String scheduleJson = null;
        if (request.getSchedule() != null) {
            try {
                scheduleJson = objectMapper.writeValueAsString(request.getSchedule());
            } catch (Exception e) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }

        recruitment.update(
                request.getTerm(),
                request.getStartDate(),
                request.getEndDate(),
                scheduleJson,
                request.getBrochureUrl()
        );

        return RecruitmentIdResponse.of(recruitment.getId());
    }

    // 모집 공고 삭제
    @Transactional
    public void deleteRecruitment(Long id) {
        Recruitment recruitment = recruitmentRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

        if (applicantRepository.existsByRecruitmentId(id) ||
                applicationQuestionRepository.existsByRecruitmentId(id)) {
            throw new CustomException(ErrorCode.RECRUITMENT_HAS_REFERENCES);
        }

        recruitmentRepository.delete(recruitment);
    }

    // 지원서 전체 삭제 (모집 진행 중이면 거부)
    @Transactional
    public void deleteApplicants(Long recruitmentId) {
        Recruitment recruitment = recruitmentRepository.findById(recruitmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

        if (recruitment.isActive(now())) {
            throw new CustomException(ErrorCode.RECRUITMENT_NOT_CLOSED);
        }

        applicantAnswerRepository.deleteByRecruitmentId(recruitmentId);
        applicantRepository.deleteByRecruitmentId(recruitmentId);
    }

    // 지원서 질문 목록 조회 (어드민 전용, 모집 중 여부 무관)
    public List<QuestionResponse> getAdminQuestions(Long recruitmentId) {
        recruitmentRepository.findById(recruitmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));
        return applicationQuestionRepository.findByRecruitmentIdOrderByOrderNumAsc(recruitmentId)
                .stream()
                .map(QuestionResponse::from)
                .toList();
    }

    // 지원서 질문 등록 (다건, 실패 시 전체 롤백)
    @Transactional
    public QuestionIdsResponse createQuestions(QuestionsCreateRequest request) {
        Recruitment recruitment = recruitmentRepository.findById(request.getRecruitmentId())
                .orElseThrow(() -> new CustomException(ErrorCode.RECRUITMENT_NOT_FOUND));

        Set<String> labelSet = new java.util.HashSet<>();
        Map<ApplicationQuestion.Category, Set<Integer>> orderNumByCategory = new java.util.HashMap<>();
        for (QuestionItemRequest item : request.getQuestions()) {
            if (!labelSet.add(item.getLabel())) throw new CustomException(ErrorCode.DUPLICATE_QUESTION_LABEL);
            if (!orderNumByCategory.computeIfAbsent(item.getCategory(), k -> new java.util.HashSet<>()).add(item.getOrderNum())) {
                throw new CustomException(ErrorCode.DUPLICATE_QUESTION_ORDER);
            }
        }

        for (QuestionItemRequest item : request.getQuestions()) {
            validateQuestionType(item.getType(), item.getLimitLength(), item.getMetadata());

            if (applicationQuestionRepository.existsByRecruitmentIdAndLabel(
                    request.getRecruitmentId(), item.getLabel())) {
                throw new CustomException(ErrorCode.DUPLICATE_QUESTION_LABEL);
            }
            if (applicationQuestionRepository.existsByRecruitmentIdAndCategoryAndOrderNum(
                    request.getRecruitmentId(), item.getCategory(), item.getOrderNum())) {
                throw new CustomException(ErrorCode.DUPLICATE_QUESTION_ORDER);
            }
        }

        List<Long> ids = new java.util.ArrayList<>();
        for (QuestionItemRequest item : request.getQuestions()) {
            String metadataJson = serializeMetadata(item.getMetadata());
            ApplicationQuestion question = ApplicationQuestion.create(
                    recruitment,
                    item.getLabel(),
                    item.getCategory(),
                    item.getType(),
                    item.getContent(),
                    item.getDescription(),
                    item.getType() == ApplicationQuestion.Type.TEXT ? item.getLimitLength() : null,
                    item.getType() == ApplicationQuestion.Type.TABLE ? metadataJson : null,
                    item.getOrderNum(),
                    item.getIsRequired()
            );
            applicationQuestionRepository.save(question);
            ids.add(question.getId());
        }

        return QuestionIdsResponse.of(ids);
    }

    // 지원서 질문 수정
    @Transactional
    public QuestionIdResponse updateQuestion(Long questionId, QuestionUpdateRequest request) {
        ApplicationQuestion question = applicationQuestionRepository.findById(questionId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUESTIONS_NOT_FOUND));

        if (request.getLabel() != null &&
                applicationQuestionRepository.existsByRecruitmentIdAndLabelAndIdNot(
                        question.getRecruitment().getId(), request.getLabel(), questionId)) {
            throw new CustomException(ErrorCode.DUPLICATE_QUESTION_LABEL);
        }
        if (request.getOrderNum() != null) {
            ApplicationQuestion.Category resolvedCategory = request.getCategory() != null ? request.getCategory() : question.getCategory();
            if (applicationQuestionRepository.existsByRecruitmentIdAndCategoryAndOrderNumAndIdNot(
                    question.getRecruitment().getId(), resolvedCategory, request.getOrderNum(), questionId)) {
                throw new CustomException(ErrorCode.DUPLICATE_QUESTION_ORDER);
            }
        }

        ApplicationQuestion.Type resolvedType = request.getType() != null ? request.getType() : question.getType();
        JsonNullable<Integer> limitLength = request.getLimitLength();
        JsonNullable<com.fasterxml.jackson.databind.JsonNode> metadata = request.getMetadata();

        if (request.getType() != null) {
            if (resolvedType == ApplicationQuestion.Type.TEXT) {
                if (!limitLength.isPresent() || limitLength.get() == null) {
                    throw new CustomException(ErrorCode.MISSING_PARAMETER);
                }
                metadata = JsonNullable.of(null);
            } else {
                if (!metadata.isPresent() || metadata.get() == null) {
                    throw new CustomException(ErrorCode.MISSING_PARAMETER);
                }
                limitLength = JsonNullable.of(null);
            }
        }

        JsonNullable<String> metadataJson = JsonNullable.undefined();
        if (metadata != null && metadata.isPresent()) {
            metadataJson = JsonNullable.of(
                    metadata.get() != null ? serializeMetadata(metadata.get()) : null
            );
        }

        question.update(
                request.getLabel(),
                request.getCategory(),
                request.getType(),
                request.getContent(),
                request.getDescription(),
                limitLength,
                metadataJson,
                request.getOrderNum(),
                request.getIsRequired()
        );

        return QuestionIdResponse.of(question.getId());
    }

    // 지원서 질문 삭제
    @Transactional
    public void deleteQuestion(Long questionId) {
        ApplicationQuestion question = applicationQuestionRepository.findById(questionId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUESTIONS_NOT_FOUND));

        if (applicantAnswerRepository.existsByQuestionId(questionId)) {
            throw new CustomException(ErrorCode.QUESTION_HAS_ANSWERS);
        }

        applicationQuestionRepository.delete(question);
    }

    private void validateQuestionType(ApplicationQuestion.Type type, Integer limitLength, com.fasterxml.jackson.databind.JsonNode metadata) {
        if (type == ApplicationQuestion.Type.TEXT && limitLength == null) {
            throw new CustomException(ErrorCode.MISSING_PARAMETER);
        }
        if (type == ApplicationQuestion.Type.TABLE && metadata == null) {
            throw new CustomException(ErrorCode.MISSING_PARAMETER);
        }
        if (type == ApplicationQuestion.Type.TABLE && metadata != null) {
            JsonNode multipleNode = metadata.path("multiple");
            if (!multipleNode.isMissingNode() && !multipleNode.isBoolean()) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
    }

    private String serializeMetadata(com.fasterxml.jackson.databind.JsonNode metadata) {
        if (metadata == null) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String serializeList(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean isMultiple(ApplicationQuestion question) {
        if (question.getMetadata() == null) return false;
        try {
            return objectMapper.readTree(question.getMetadata()).path("multiple").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode dedupeMultipleAnswer(JsonNode answer) {
        com.fasterxml.jackson.databind.node.ObjectNode result = objectMapper.createObjectNode();
        answer.fields().forEachRemaining(entry -> {
            JsonNode val = entry.getValue();
            if (val.isArray()) {
                Set<String> seen = new LinkedHashSet<>();
                val.forEach(elem -> seen.add(elem.asText()));
                ArrayNode deduped = objectMapper.createArrayNode();
                seen.forEach(deduped::add);
                result.set(entry.getKey(), deduped);
            } else {
                result.set(entry.getKey(), val);
            }
        });
        return result;
    }

    // 모집 사전 알림 신청 목록 조회 (어드민 전용)
    public List<SubscriptionResponse> getAllSubscriptions() {
        return subscriptionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    // 모집 사전 알림 신청 전체 삭제 (어드민 전용)
    @Transactional
    public void deleteAllSubscriptions() {
        subscriptionRepository.deleteAll();
    }
}
