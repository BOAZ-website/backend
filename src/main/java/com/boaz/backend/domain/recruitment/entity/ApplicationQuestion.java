package com.boaz.backend.domain.recruitment.entity;

import com.boaz.backend.global.common.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "application_question")
public class ApplicationQuestion extends BaseEntity {

    @Id
    @Column(length = 10)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruitment_id", nullable = false)
    private Recruitment recruitment;

    @Enumerated(EnumType.STRING)
    private QuestionCategory category;

    @Enumerated(EnumType.STRING)
    private QuestionType type;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "JSON")
    private String metadata;

    private Integer limitLength;

    @Column(nullable = false)
    private Integer orderNum;

    @Column(nullable = false)
    private Boolean isRequired;
}