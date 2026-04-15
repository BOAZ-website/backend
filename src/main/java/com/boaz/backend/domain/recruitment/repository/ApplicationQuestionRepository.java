package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.lang.String;
import java.util.List;

public interface ApplicationQuestionRepository extends JpaRepository<ApplicationQuestion, String> {

    @Query("SELECT q FROM ApplicationQuestion q " +
        "WHERE q.recruitment.id = :recruitmentId " +
        "AND (q.category = :commonCategory OR q.category = :trackCategory) " +
        "ORDER BY q.orderNum ASC")
    List<ApplicationQuestion> findByRecruitmentIdAndCategories(
        @Param("recruitmentId") Long recruitmentId,
        @Param("commonCategory") ApplicationQuestion.Category commonCategory,
        @Param("trackCategory") ApplicationQuestion.Category trackCategory
    );
}