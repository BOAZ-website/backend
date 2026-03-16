package com.boaz.backend.domain.curriculum.entity;

import com.boaz.backend.global.common.BaseEntity;
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

    public enum Track {
        ANALYSIS, VISUALIZATION, ENGINEERING
    }
}
