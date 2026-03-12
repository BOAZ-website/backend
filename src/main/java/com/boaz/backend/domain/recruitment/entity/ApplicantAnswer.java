package com.boaz.backend.domain.recruitment.entity;

import com.boaz.backend.global.common.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "applicant_answer")
public class ApplicantAnswer extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private ApplicationQuestion question;

    @Column(columnDefinition = "TEXT")
    private String answerText;

    @Column(columnDefinition = "JSON")
    private String answerJson;

    @Builder
    public ApplicantAnswer(Applicant applicant, ApplicationQuestion question,
                            String answerText, String answerJson) {
        this.applicant = applicant;
        this.question = question;
        this.answerText = answerText;
        this.answerJson = answerJson;
    }
}
