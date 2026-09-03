package com.boaz.backend.domain.user.service;

import com.boaz.backend.domain.recruitment.entity.Applicant;
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
