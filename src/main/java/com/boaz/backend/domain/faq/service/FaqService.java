package com.boaz.backend.domain.faq.service;

import com.boaz.backend.domain.faq.dto.FaqResponse;
import com.boaz.backend.domain.faq.entity.Faq;
import com.boaz.backend.domain.faq.repository.FaqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

    public List<FaqResponse> getFaqs(Faq.Category category) {
        return faqRepository.findAllByCategoryOrderByOrderNumAsc(category)
                .stream()
                .map(FaqResponse::from)
                .toList();
    }
}