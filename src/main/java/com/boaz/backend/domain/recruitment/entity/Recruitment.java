package com.boaz.backend.domain.recruitment.entity;

import com.boaz.backend.global.common.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "recruitment")
public class Recruitment extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer term;
}
