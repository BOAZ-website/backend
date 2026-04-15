package com.boaz.backend.domain.recruitment.entity;

import com.boaz.backend.global.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "application_question")
public class ApplicationQuestion extends BaseEntity {

    public enum Category {
        @Schema(description = "공통")
        COMMON,
        @Schema(description = "분석")
        ANALYSIS,
        @Schema(description = "시각화")
        VISUALIZATION,
        @Schema(description = "엔지니어링")
        ENGINEERING
    }

    public enum Type {
        @Schema(description = "텍스트")
        TEXT,
        @Schema(description = "표")
        TABLE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruitment_id", nullable = false)
    private Recruitment recruitment;
    
    @Column(length = 20)
    private String label;

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