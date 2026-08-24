package com.boaz.backend.domain.faq.integration;

import com.boaz.backend.domain.faq.dto.request.FaqCreateRequest;
import com.boaz.backend.domain.faq.dto.request.FaqUpdateRequest;
import com.boaz.backend.domain.faq.dto.response.FaqResponse;
import com.boaz.backend.domain.faq.entity.Faq;
import com.boaz.backend.domain.faq.repository.FaqRepository;
import com.boaz.backend.domain.faq.service.FaqService;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.support.TestcontainersBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class FaqIntegrationTest extends TestcontainersBase {

    @Autowired FaqService faqService;
    @Autowired FaqRepository faqRepository;

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private Faq saveFaq(Faq.Category category, int orderNum) {
        return faqRepository.save(Faq.create("질문 " + orderNum, "답변 " + orderNum, category, orderNum));
    }

    private FaqCreateRequest makeCreateRequest(String question, String answer,
                                               Faq.Category category, int orderNum) {
        FaqCreateRequest req = new FaqCreateRequest();
        ReflectionTestUtils.setField(req, "question", question);
        ReflectionTestUtils.setField(req, "answer", answer);
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
    @DisplayName("FAQ 목록 조회 end-to-end (FAQ-001)")
    class GetFaqs {

        @Test
        @DisplayName("TC-001 RECRUITMENT 카테고리 → orderNum 오름차순 반환, 타 카테고리 미포함")
        void orderedByOrderNum() {
            saveFaq(Faq.Category.RECRUITMENT, 2);
            saveFaq(Faq.Category.RECRUITMENT, 1);
            saveFaq(Faq.Category.ACTIVITY, 1);  // 다른 카테고리

            List<FaqResponse> result = faqService.getFaqs(Faq.Category.RECRUITMENT);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getOrderNum()).isEqualTo(1);
            assertThat(result.get(1).getOrderNum()).isEqualTo(2);
            assertThat(result).noneMatch(r -> r.getCategory().equals("ACTIVITY"));
        }

        @Test
        @DisplayName("TC-002 해당 카테고리 데이터 없을 때 → 빈 배열 반환")
        void emptyCategory() {
            List<FaqResponse> result = faqService.getFaqs(Faq.Category.ETC);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════
    // FAQ-002: FAQ 등록
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("FAQ 등록 end-to-end (FAQ-002)")
    class CreateFaq {

        @Test
        @DisplayName("TC-003 FAQ 등록 성공 → DB에 저장됨")
        void success() {
            FaqResponse res = faqService.createFaq(
                    makeCreateRequest("지원 방법?", "홈페이지를 통해 지원", Faq.Category.RECRUITMENT, 1));

            assertThat(res.getOrderNum()).isEqualTo(1);
            assertThat(faqRepository.existsByCategoryAndOrderNum(Faq.Category.RECRUITMENT, 1)).isTrue();
        }

        @Test
        @DisplayName("TC-004 동일 카테고리 내 orderNum 중복 → DUPLICATE_ORDER_NUM, DB 미저장")
        void duplicateOrderNum() {
            saveFaq(Faq.Category.RECRUITMENT, 1);

            assertThatThrownBy(() -> faqService.createFaq(
                    makeCreateRequest("다른 질문", "다른 답변", Faq.Category.RECRUITMENT, 1)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_ORDER_NUM);

            assertThat(faqRepository.findAllByCategoryOrderByOrderNumAsc(Faq.Category.RECRUITMENT)).hasSize(1);
        }

        @Test
        @DisplayName("다른 카테고리에 동일 orderNum 있어도 → 등록 성공")
        void differentCategorySameOrderNum() {
            saveFaq(Faq.Category.ACTIVITY, 1);

            FaqResponse res = faqService.createFaq(
                    makeCreateRequest("질문", "답변", Faq.Category.RECRUITMENT, 1));

            assertThat(res).isNotNull();
            assertThat(faqRepository.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("TC-011 동시에 같은 category+orderNum으로 등록 요청 → 단 하나만 성공, 나머지는 DUPLICATE_ORDER_NUM, DB에는 1건만 남음")
        void concurrentCreateSameCategoryOrderNum() throws Exception {
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger duplicateCount = new AtomicInteger();

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        faqService.createFaq(
                                makeCreateRequest("질문", "답변", Faq.Category.RECRUITMENT, 1));
                        successCount.incrementAndGet();
                    } catch (CustomException e) {
                        if (e.getErrorCode() == ErrorCode.DUPLICATE_ORDER_NUM) {
                            duplicateCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);

            try {
                assertThat(successCount.get()).isEqualTo(1);
                assertThat(duplicateCount.get()).isEqualTo(threadCount - 1);
                assertThat(faqRepository.count()).isEqualTo(1);
            } finally {
                // 워커 스레드의 성공한 저장은 메인 테스트 트랜잭션(롤백 대상) 밖에서 독립적으로 커밋되므로,
                // 이 클래스가 공유하는 Spring 컨텍스트/DB에 영구히 남지 않도록 별도 커밋으로 명시적으로 정리한다.
                executor.submit(() -> faqRepository.deleteAll()).get(5, TimeUnit.SECONDS);
                executor.shutdown();
            }
        }
    }

    // ══════════════════════════════════════════════
    // FAQ-003: FAQ 수정
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("FAQ 수정 end-to-end (FAQ-003)")
    class UpdateFaq {

        @Test
        @DisplayName("TC-005 question만 부분 수정 → answer·orderNum 기존 값 유지")
        void partialUpdate() {
            Faq saved = saveFaq(Faq.Category.RECRUITMENT, 1);

            FaqResponse res = faqService.updateFaq(saved.getId(),
                    makeUpdateRequest("변경된 질문", null, null, null));

            assertThat(res.getQuestion()).isEqualTo("변경된 질문");
            assertThat(res.getAnswer()).isEqualTo("답변 1");
            assertThat(res.getOrderNum()).isEqualTo(1);
        }

        @Test
        @DisplayName("TC-006 자기 자신의 orderNum으로 수정 → 중복 에러 미발생")
        void selfOrderNum() {
            Faq saved = saveFaq(Faq.Category.RECRUITMENT, 1);

            FaqResponse res = faqService.updateFaq(saved.getId(),
                    makeUpdateRequest(null, null, null, 1));

            assertThat(res.getOrderNum()).isEqualTo(1);
        }

        @Test
        @DisplayName("TC-007 존재하지 않는 faqId → FAQ_NOT_FOUND")
        void notFound() {
            assertThatThrownBy(() -> faqService.updateFaq(999L,
                    makeUpdateRequest("수정", null, null, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FAQ_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-008 수정 후 동일 카테고리 내 orderNum 충돌 → DUPLICATE_ORDER_NUM")
        void duplicateOnUpdate() {
            saveFaq(Faq.Category.RECRUITMENT, 1);
            Faq target = saveFaq(Faq.Category.RECRUITMENT, 2);

            assertThatThrownBy(() -> faqService.updateFaq(target.getId(),
                    makeUpdateRequest(null, null, null, 1)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_ORDER_NUM);
        }
    }

    // ══════════════════════════════════════════════
    // FAQ-004: FAQ 삭제
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("FAQ 삭제 end-to-end (FAQ-004)")
    class DeleteFaq {

        @Test
        @DisplayName("TC-009 FAQ 삭제 성공 → DB에서 제거됨")
        void success() {
            Faq saved = saveFaq(Faq.Category.RECRUITMENT, 1);

            faqService.deleteFaq(saved.getId());

            assertThat(faqRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("TC-010 존재하지 않는 faqId → FAQ_NOT_FOUND")
        void notFound() {
            assertThatThrownBy(() -> faqService.deleteFaq(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FAQ_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════════════
    // 생명주기 통합 시나리오
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("FAQ 전체 생명주기 시나리오")
    class Lifecycle {

        @Test
        @DisplayName("등록 → 조회 → 수정 → 삭제 흐름이 DB와 일치")
        void fullLifecycle() {
            // 등록
            FaqResponse created = faqService.createFaq(
                    makeCreateRequest("최초 질문", "최초 답변", Faq.Category.ACTIVITY, 1));
            assertThat(faqRepository.count()).isEqualTo(1);

            // 조회
            List<FaqResponse> list = faqService.getFaqs(Faq.Category.ACTIVITY);
            assertThat(list).hasSize(1);
            assertThat(list.get(0).getQuestion()).isEqualTo("최초 질문");

            // 수정
            FaqResponse updated = faqService.updateFaq(created.getId(),
                    makeUpdateRequest("수정된 질문", null, null, null));
            assertThat(updated.getQuestion()).isEqualTo("수정된 질문");
            assertThat(updated.getAnswer()).isEqualTo("최초 답변");  // 기존 값 유지

            // 삭제
            faqService.deleteFaq(created.getId());
            assertThat(faqRepository.findById(created.getId())).isEmpty();
            assertThat(faqService.getFaqs(Faq.Category.ACTIVITY)).isEmpty();
        }
    }
}
