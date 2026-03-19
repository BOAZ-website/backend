package com.boaz.backend.domain.faq.repository;

import com.boaz.backend.domain.faq.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    List<Faq> findAllByCategoryOrderByOrderNumAsc(Faq.Category category);
}
