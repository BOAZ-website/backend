package com.boaz.backend.domain.curriculum.entity;

import com.boaz.backend.global.common.BaseEntity;
import com.boaz.backend.global.common.enums.Track;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "curriculum")
public class Curriculum extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Track track;

    @Column(columnDefinition = "JSON")
    private String curriculumSteps;

    public static Curriculum create(Track track, String curriculumSteps) {
        Curriculum curriculum = new Curriculum();
        curriculum.track = track;
        curriculum.curriculumSteps = curriculumSteps;
        return curriculum;
    }

    public void updateSteps(String curriculumSteps) {
        this.curriculumSteps = curriculumSteps;
    }
}
