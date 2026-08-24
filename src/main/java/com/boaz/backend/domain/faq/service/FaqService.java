package com.boaz.backend.domain.faq.service;

import com.boaz.backend.domain.faq.dto.request.FaqCreateRequest;
import com.boaz.backend.domain.faq.dto.request.FaqUpdateRequest;
import com.boaz.backend.domain.faq.dto.response.FaqResponse;
import com.boaz.backend.domain.faq.entity.Faq;
import com.boaz.backend.domain.faq.repository.FaqRepository;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

    @Transactional
    public FaqResponse createFaq(FaqCreateRequest request) {
        if (faqRepository.existsByCategoryAndOrderNum(request.getCategory(), request.getOrderNum())) {
            throw new CustomException(ErrorCode.DUPLICATE_ORDER_NUM);
        }
        Faq faq = Faq.create(
                request.getQuestion(),
                request.getAnswer(),
                request.getCategory(),
                request.getOrderNum()
        );
        try {
            faqRepository.save(faq);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_ORDER_NUM);
        }
        return FaqResponse.from(faq);
    }

    @Transactional
    public FaqResponse updateFaq(Long faqId, FaqUpdateRequest request) {
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAQ_NOT_FOUND));
        Faq.Category targetCategory = request.getCategory() != null ? request.getCategory() : faq.getCategory();
        Integer targetOrderNum = request.getOrderNum() != null ? request.getOrderNum() : faq.getOrderNum();
        if (faqRepository.existsByCategoryAndOrderNumAndIdNot(targetCategory, targetOrderNum, faqId)) {
            throw new CustomException(ErrorCode.DUPLICATE_ORDER_NUM);
        }
        faq.update(request.getQuestion(), request.getAnswer(), request.getCategory(), request.getOrderNum());
        try {
            faqRepository.saveAndFlush(faq);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_ORDER_NUM);
        }
        return FaqResponse.from(faq);
    }

    @Transactional
    public void deleteFaq(Long faqId) {
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAQ_NOT_FOUND));
        faqRepository.delete(faq);
    }

    public List<FaqResponse> getFaqs(Faq.Category category) {
        return faqRepository.findAllByCategoryOrderByOrderNumAsc(category)
                .stream()
                .map(FaqResponse::from)
                .toList();
    }
}