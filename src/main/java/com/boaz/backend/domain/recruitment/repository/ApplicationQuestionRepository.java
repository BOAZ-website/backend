package com.boaz.backend.domain.recruitment.repository;

import com.boaz.backend.domain.recruitment.entity.ApplicationQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApplicationQuestionRepository extends JpaRepository<ApplicationQuestion, Long> {

    // 공고에 연관된 질문 존재 여부 확인
    boolean existsByRecruitmentId(Long recruitmentId);

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