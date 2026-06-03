package com.boaz.backend.domain.faq.service;

import com.boaz.backend.domain.faq.dto.request.FaqCreateRequest;
import com.boaz.backend.domain.faq.dto.request.FaqUpdateRequest;
import com.boaz.backend.domain.faq.dto.response.FaqResponse;
import com.boaz.backend.domain.faq.entity.Faq;
import com.boaz.backend.domain.faq.repository.FaqRepository;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @InjectMocks FaqService faqService;
    @Mock FaqRepository faqRepository;

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private Faq makeFaq(Long id, Faq.Category category, int orderNum) {
        Faq faq = Faq.create("질문 " + id, "답변 " + id, category, orderNum);
        ReflectionTestUtils.setField(faq, "id", id);
        return faq;
    }

    private FaqCreateRequest makeCreateRequest(Faq.Category category, int orderNum) {
        FaqCreateRequest req = new FaqCreateRequest();
        ReflectionTestUtils.setField(req, "question", "테스트 질문");
        ReflectionTestUtils.setField(req, "answer", "테스트 답변");
        ReflectionTestUtils.setField(req, "category", category);
        ReflectionTestUtils.setField(req, "orderNum", orderNum);
        return req;
    }

    private FaqUpdateRequest makeUpdateRequest(String question, String answer,
                                               Faq.Category category, Integer orderNum) {
        FaqUpdateRequest req = new FaqUpdateRequest();
        ReflectionTestUtils.setField(req, "question", question);
        ReflectionTestUtils.setField(req, "answer", answer);
        ReflectionTestUtils.setField(req, "category", category);
        ReflectionTestUtils.setField(req, "orderNum", orderNum);
        return req;
    }

    // ══════════════════════════════════════════════
    // FAQ-001: FAQ 목록 조회
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("FAQ-001 FAQ 목록 조회")
    class GetFaqs {

        @Test
        @DisplayName("TC-001 특정 카테고리 조회 → orderNum 오름차순 반환")
        void success() {
            Faq f1 = makeFaq(1L, Faq.Category.RECRUITMENT, 1);
            Faq f2 = makeFaq(2L, Faq.Category.RECRUITMENT, 2);
            when(faqRepository.findAllByCategoryOrderByOrderNumAsc(Faq.Category.RECRUITMENT))
                    .thenReturn(List.of(f1, f2));

            List<FaqResponse> result = faqService.getFaqs(Faq.Category.RECRUITMENT);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getOrderNum()).isEqualTo(1);
            assertThat(result.get(1).getOrderNum()).isEqualTo(2);
            assertThat(result).extracting(FaqResponse::getCategory).containsOnly("RECRUITMENT");
        }

        @Test
        @DisplayName("TC-002 해당 카테고리 데이터 없을 때 → 빈 배열 반환 (예외 없음)")
        void emptyCategory() {
            when(faqRepository.findAllByCategoryOrderByOrderNumAsc(Faq.Category.ETC))
                    .thenReturn(List.of());

            List<FaqResponse> result = faqService.getFaqs(Faq.Category.ETC);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════
    // FAQ-002: FAQ 등록
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("FAQ-002 FAQ 등록")
    class CreateFaq {

        @Test
        @DisplayName("TC-003 FAQ 등록 성공 → 저장 후 응답 반환")
        void success() {
            when(faqRepository.existsByCategoryAndOrderNum(Faq.Category.RECRUITMENT, 1)).thenReturn(false);
            when(faqRepository.save(any())).thenAnswer(inv -> {
                Faq f = inv.getArgument(0);
                ReflectionTestUtils.setField(f, "id", 1L);
                return f;
            });

            FaqResponse res = faqService.createFaq(makeCreateRequest(Faq.Category.RECRUITMENT, 1));

            assertThat(res.getOrderNum()).isEqualTo(1);
            assertThat(res.getCategory()).isEqualTo("RECRUITMENT");
            verify(faqRepository).save(any(Faq.class));
        }

        @Test
        @DisplayName("TC-004 동일 카테고리 내 orderNum 중복 → DUPLICATE_ORDER_NUM, 저장 안 함")
        void duplicateOrderNum() {
            when(faqRepository.existsByCategoryAndOrderNum(Faq.Category.RECRUITMENT, 1)).thenReturn(true);

            assertThatThrownBy(() -> faqService.createFaq(makeCreateRequest(Faq.Category.RECRUITMENT, 1)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_ORDER_NUM);

            verify(faqRepository, never()).save(any());
        }

        @Test
        @DisplayName("다른 카테고리에 동일 orderNum 있어도 → RECRUITMENT 등록 성공")
        void differentCategorySameOrderNum() {
            when(faqRepository.existsByCategoryAndOrderNum(Faq.Category.RECRUITMENT, 1)).thenReturn(false);
            when(faqRepository.save(any())).thenAnswer(inv -> {
                Faq f = inv.getArgument(0);
                ReflectionTestUtils.setField(f, "id", 2L);
                return f;
            });

            FaqResponse res = faqService.createFaq(makeCreateRequest(Faq.Category.RECRUITMENT, 1));

            assertThat(res).isNotNull();
        }
    }

    // ══════════════════════════════════════════════
    // FAQ-003: FAQ 수정
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("FAQ-003 FAQ 수정")
    class UpdateFaq {

        @Test
        @DisplayName("TC-005 question만 부분 수정 → answer·category·orderNum 기존 값 유지")
        void partialUpdateQuestion() {
            Faq faq = makeFaq(1L, Faq.Category.RECRUITMENT, 1);
            when(faqRepository.findById(1L)).thenReturn(Optional.of(faq));
            when(faqRepository.existsByCategoryAndOrderNumAndIdNot(Faq.Category.RECRUITMENT, 1, 1L))
                    .thenReturn(false);

            FaqResponse res = faqService.updateFaq(1L, makeUpdateRequest("변경된 질문", null, null, null));

            assertThat(res.getQuestion()).isEqualTo("변경된 질문");
            assertThat(res.getAnswer()).isEqualTo("답변 1");
            assertThat(res.getOrderNum()).isEqualTo(1);
        }

        @Test
        @DisplayName("TC-006 자기 자신의 orderNum으로 수정 → 중복 에러 미발생")
        void selfOrderNum() {
            Faq faq = makeFaq(1L, Faq.Category.RECRUITMENT, 1);
            when(faqRepository.findById(1L)).thenReturn(Optional.of(faq));
            when(faqRepository.existsByCategoryAndOrderNumAndIdNot(Faq.Category.RECRUITMENT, 1, 1L))
                    .thenReturn(false);

            FaqResponse res = faqService.updateFaq(1L, makeUpdateRequest(null, null, null, 1));

            assertThat(res.getOrderNum()).isEqualTo(1);
        }

        @Test
        @DisplayName("TC-007 존재하지 않는 faqId → FAQ_NOT_FOUND")
        void notFound() {
            when(faqRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> faqService.updateFaq(999L, makeUpdateRequest("수정", null, null, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FAQ_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-008 수정 후 동일 카테고리 내 orderNum 충돌 → DUPLICATE_ORDER_NUM")
        void duplicateOnUpdate() {
            Faq faq = makeFaq(2L, Faq.Category.RECRUITMENT, 2);
            when(faqRepository.findById(2L)).thenReturn(Optional.of(faq));
            when(faqRepository.existsByCategoryAndOrderNumAndIdNot(Faq.Category.RECRUITMENT, 1, 2L))
                    .thenReturn(true);

            assertThatThrownBy(() -> faqService.updateFaq(2L, makeUpdateRequest(null, null, null, 1)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_ORDER_NUM);
        }
    }

    // ══════════════════════════════════════════════
    // FAQ-004: FAQ 삭제
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("FAQ-004 FAQ 삭제")
    class DeleteFaq {

        @Test
        @DisplayName("TC-009 FAQ 삭제 성공 → repository.delete 호출")
        void success() {
            Faq faq = makeFaq(1L, Faq.Category.RECRUITMENT, 1);
            when(faqRepository.findById(1L)).thenReturn(Optional.of(faq));

            faqService.deleteFaq(1L);

            verify(faqRepository).delete(faq);
        }

        @Test
        @DisplayName("TC-010 존재하지 않는 faqId → FAQ_NOT_FOUND, 삭제 미호출")
        void notFound() {
            when(faqRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> faqService.deleteFaq(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FAQ_NOT_FOUND);

            verify(faqRepository, never()).delete(any());
        }
    }
}
