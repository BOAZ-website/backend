package com.boaz.backend.domain.archive.entity;

import com.boaz.backend.global.common.BaseEntity;
import com.boaz.backend.global.common.enums.Track;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "archive")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Archive extends BaseEntity {

    // 아카이브 카테고리 (프로젝트, 활동사진, 기술블로그)
    public enum Category {
        PROJECT, ACTIVITY, BLOG
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer term;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Category category;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 100)
    private String teamName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Track track;

    @Column(nullable = false, length = 255)
    private String imageUrl;

    @Column(nullable = false, columnDefinition = "json")
    private String links;

    @Column(nullable = true)
    private LocalDate contentDate;

    @Builder
    public Archive(
        Integer term, 
        Category category,
        String title, 
        String teamName, 
        Track track, 
        String imageUrl, 
        String links, 
        LocalDate contentDate 
    ) {
        this.term = term;
        this.category = category;
        this.title = title;
        this.teamName = teamName;
        this.track = track;
        this.imageUrl = imageUrl;
        this.links = links;
        this.contentDate = contentDate;
    }
}
