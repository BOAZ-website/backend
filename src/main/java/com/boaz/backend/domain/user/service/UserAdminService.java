package com.boaz.backend.domain.user.service;

import com.boaz.backend.domain.recruitment.entity.Applicant;
import com.boaz.backend.domain.recruitment.entity.EvaluationDecision;
import com.boaz.backend.domain.recruitment.repository.ApplicantRepository;
import com.boaz.backend.domain.user.dto.response.PromoteUsersResponse;
import com.boaz.backend.domain.user.entity.User;
import com.boaz.backend.domain.user.repository.UserRepository;
import com.boaz.backend.global.common.enums.MemberType;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final ApplicantRepository applicantRepository;
    private final TransactionTemplate transactionTemplate;

    public PromoteUsersResponse bulkPromote(List<Long> userIds) {
        List<PromoteUsersResponse.FailedUserInfo> failed = new ArrayList<>();

        for (Long userId : userIds) {
            try {
                transactionTemplate.execute(status -> {
                    promoteOne(userId);
                    return null;
                });
            } catch (CustomException e) {
                // 비즈니스 오류: skip 후 실패 목록에 추가
                failed.add(new PromoteUsersResponse.FailedUserInfo(userId, e.getErrorCode().getCode()));
            }
            // RuntimeException(서버 오류)은 그대로 전파 → 즉시 중단
        }

        return PromoteUsersResponse.of(failed);
    }

    private void promoteOne(Long userId) {
        // 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 이미 MEMBER인 경우
        if (user.getMemberType() == MemberType.MEMBER) {
            throw new CustomException(ErrorCode.ALREADY_MEMBER);
        }

        // SUBMITTED 지원서 조회 (recruitment JOIN FETCH로 term 접근)
        Applicant applicant = applicantRepository
                .findByUserIdAndStatus(userId, Applicant.ApplicantStatus.SUBMITTED)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        // 최종 합격(PASS)자만 승격 — 설계 문서가 정한 것은 권한 매핑(MEMBER_PROMOTION_WRITE)까지이고,
        // 이 검사는 기능명("합격자 정회원 승격")과 데이터 무결성을 위해 추가한 비즈니스 검증이다.
        // 인가(누가 승격할 수 있나)는 컨트롤러 @PreAuthorize, 대상 검증(이 지원자가 승격 대상인가)은 여기서 본다.
        if (applicant.getFinalDecision() != EvaluationDecision.PASS) {
            throw new CustomException(ErrorCode.APPLICANT_NOT_PASSED);
        }

        // 승격: 지원서 개인정보 → User 복사 + memberType → MEMBER
        user.promote(
                applicant.getName(),
                applicant.getEmail(),
                applicant.getPhone(),
                applicant.getUniversity(),
                applicant.getMajor(),
                applicant.getMinorDoubleMajor(),
                applicant.getLastSemester(),
                applicant.getMilitaryStatus(),
                applicant.getBirthDate(),
                applicant.getGraduationDate(),
                applicant.getGradSchoolPlan(),
                applicant.getTrack(),
                applicant.getRecruitment().getTerm()
        );
    }
}
