package com.boaz.backend.domain.review.entity;

import com.boaz.backend.global.common.BaseEntity;
import com.boaz.backend.global.common.enums.Track;

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

    public static Review create(String name, Track track, Integer term, String content, String imageUrl) {
        Review review = new Review();
        review.name = name;
        review.track = track;
        review.term = term;
        review.content = content;
        review.imageUrl = imageUrl;
        return review;
    }

    public void update(String name, Track track, Integer term, String content, String imageUrl) {
        if (name != null) this.name = name;
        if (track != null) this.track = track;
        if (term != null) this.term = term;
        if (content != null) this.content = content;
        if (imageUrl != null) this.imageUrl = imageUrl;
    }
}
