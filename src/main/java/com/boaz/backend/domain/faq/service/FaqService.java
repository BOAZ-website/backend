package com.boaz.backend.domain.faq.service;

import com.boaz.backend.domain.faq.dto.FaqResponse;
import com.boaz.backend.domain.faq.entity.Faq;
import com.boaz.backend.domain.faq.repository.FaqRepository;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

    public List<FaqResponse> getFaqs(String category) {
        Faq.Category categoryEnum;
        try {
            categoryEnum = Faq.Category.valueOf(category);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_PARAMETER);
        }

        return faqRepository.findAllByCategoryOrderByOrderNumAsc(categoryEnum)
                .stream()
                .map(FaqResponse::from)
                .toList();
    }
}
