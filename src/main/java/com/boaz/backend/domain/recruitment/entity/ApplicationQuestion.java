package com.boaz.backend.domain.recruitment.entity;

import com.boaz.backend.global.common.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "application_question")
public class ApplicationQuestion extends BaseEntity {

    //공통, 시각화, 분석, 엔지니어링
    public enum Category {
        COMMON, ANALYSIS, VISUALIZATION, ENGINEERING
    }

    public enum Type {
        TEXT, TABLE
    }

    @Id
    @Column(length = 20)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruitment_id", nullable = false)
    private Recruitment recruitment;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Type type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "JSON")
    private String metadata;

    private Integer limitLength;

    @Column(nullable = false)
    private Integer orderNum;

    @Column(nullable = false)
    private Boolean isRequired;
}