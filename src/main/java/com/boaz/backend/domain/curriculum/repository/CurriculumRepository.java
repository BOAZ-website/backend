package com.boaz.backend.domain.curriculum.repository;

import com.boaz.backend.domain.curriculum.entity.Curriculum;
import com.boaz.backend.global.common.enums.Track;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CurriculumRepository extends JpaRepository<Curriculum, Long> {

    List<Curriculum> findAll();

    Optional<Curriculum> findByTrack(Track track);
}
