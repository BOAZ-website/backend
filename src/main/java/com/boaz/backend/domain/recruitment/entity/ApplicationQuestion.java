package com.boaz.backend.domain.recruitment.entity;

import com.boaz.backend.global.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import org.openapitools.jackson.nullable.JsonNullable;

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

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "JSON")
    private String metadata;

    private Integer limitLength;

    @Column(nullable = false)
    private Integer orderNum;

    @Column(nullable = false)
    private Boolean isRequired;

    public static ApplicationQuestion create(Recruitment recruitment, String label,
            Category category, Type type, String content, String description,
            Integer limitLength, String metadata, Integer orderNum, Boolean isRequired) {
        ApplicationQuestion q = new ApplicationQuestion();
        q.recruitment = recruitment;
        q.label = label;
        q.category = category;
        q.type = type;
        q.content = content;
        q.description = description;
        q.limitLength = limitLength;
        q.metadata = metadata;
        q.orderNum = orderNum;
        q.isRequired = isRequired;
        return q;
    }

    public void update(String label, Category category, Type type, String content,
            JsonNullable<String> description, JsonNullable<Integer> limitLength,
            JsonNullable<String> metadata, Integer orderNum, Boolean isRequired) {
        if (label != null) this.label = label;
        if (category != null) this.category = category;
        if (type != null) this.type = type;
        if (content != null) this.content = content;
        if (description != null && description.isPresent()) this.description = description.get();
        if (limitLength != null && limitLength.isPresent()) this.limitLength = limitLength.get();
        if (metadata != null && metadata.isPresent()) this.metadata = metadata.get();
        if (orderNum != null) this.orderNum = orderNum;
        if (isRequired != null) this.isRequired = isRequired;
    }
}