package com.boaz.backend.domain.review.entity;

import com.boaz.backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "review")
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private Track track;

    private Integer term;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String imageUrl;

    public enum Track {
        시각화, 분석, 엔지니어링
    }
}
