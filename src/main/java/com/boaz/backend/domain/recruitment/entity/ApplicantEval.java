package com.boaz.backend.domain.recruitment.entity;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.common.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "applicant_eval",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_applicant_eval_applicant_admin",
                columnNames = {"applicant_id", "admin_id"}
        )
)
public class ApplicantEval extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvaluationDecision decision = EvaluationDecision.PENDING;

    // 점수: 미평가 시 null (집계 시 합산 제외)
    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String memo;

    // 면접 질문: 평가자가 면접 때 물어볼 질문 (memo와 동일 성격, nullable)
    @Column(columnDefinition = "TEXT")
    private String interviewQuestion;

    @Builder
    public ApplicantEval(Applicant applicant, Admin admin, EvaluationDecision decision,
                         Integer score, String memo, String interviewQuestion) {
        this.applicant = applicant;
        this.admin = admin;
        this.decision = decision != null ? decision : EvaluationDecision.PENDING;
        this.score = score;
        this.memo = memo;
        this.interviewQuestion = interviewQuestion;
    }

    // 저장(upsert) 시 보낸 값으로 전체 반영
    public void update(EvaluationDecision decision, Integer score, String memo, String interviewQuestion) {
        this.decision = decision != null ? decision : EvaluationDecision.PENDING;
        this.score = score;
        this.memo = memo;
        this.interviewQuestion = interviewQuestion;
    }
}
