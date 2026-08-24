package com.boaz.backend.domain.curriculum.integration;

import com.boaz.backend.domain.curriculum.dto.request.CurriculumCreateRequest;
import com.boaz.backend.domain.curriculum.dto.request.CurriculumStepRequest;
import com.boaz.backend.domain.curriculum.dto.request.CurriculumUpdateRequest;
import com.boaz.backend.domain.curriculum.dto.response.CurriculumResponse;
import com.boaz.backend.domain.curriculum.entity.Curriculum;
import com.boaz.backend.domain.curriculum.repository.CurriculumRepository;
import com.boaz.backend.domain.curriculum.service.CurriculumService;
import com.boaz.backend.global.common.enums.Track;
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
class CurriculumIntegrationTest extends TestcontainersBase {

    @Autowired CurriculumService curriculumService;
    @Autowired CurriculumRepository curriculumRepository;

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private Curriculum saveCurriculum(Track track) {
        String stepsJson = "[{\"step\":1,\"title\":\"제목\",\"desc\":\"설명\"}]";
        return curriculumRepository.save(Curriculum.create(track, stepsJson));
    }

    private CurriculumStepRequest makeStep(int step, String title, String desc) {
        CurriculumStepRequest s = new CurriculumStepRequest();
        ReflectionTestUtils.setField(s, "step", step);
        ReflectionTestUtils.setField(s, "title", title);
        ReflectionTestUtils.setField(s, "desc", desc);
        return s;
    }

    private CurriculumCreateRequest makeCreateRequest(Track track, List<CurriculumStepRequest> steps) {
        CurriculumCreateRequest req = new CurriculumCreateRequest();
        ReflectionTestUtils.setField(req, "track", track);
        ReflectionTestUtils.setField(req, "curriculumSteps", steps);
        return req;
    }

    private CurriculumUpdateRequest makeUpdateRequest(List<CurriculumStepRequest> steps) {
        CurriculumUpdateRequest req = new CurriculumUpdateRequest();
        ReflectionTestUtils.setField(req, "curriculumSteps", steps);
        return req;
    }

    // ══════════════════════════════════════════════
    // CUR-001: 커리큘럼 조회
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("커리큘럼 조회 end-to-end (CUR-001)")
    class GetCurriculums {

        @Test
        @DisplayName("TC-001 track 미입력 → 저장된 전체 반환")
        void allTracks() {
            saveCurriculum(Track.ANALYSIS);
            saveCurriculum(Track.VISUALIZATION);

            List<CurriculumResponse> result = curriculumService.getCurriculums(null);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("TC-002 track=ANALYSIS 필터 → 해당 트랙만 반환")
        void specificTrack() {
            saveCurriculum(Track.ANALYSIS);
            saveCurriculum(Track.ENGINEERING);

            List<CurriculumResponse> result = curriculumService.getCurriculums(Track.ANALYSIS);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTrack()).isEqualTo("ANALYSIS");
        }

        @Test
        @DisplayName("TC-003 track=ALL → INVALID_TRACK_SELECTION")
        void trackAll() {
            assertThatThrownBy(() -> curriculumService.getCurriculums(Track.ALL))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);
        }

        @Test
        @DisplayName("TC-004 해당 track 데이터 없을 때 → 빈 배열")
        void noData() {
            List<CurriculumResponse> result = curriculumService.getCurriculums(Track.ENGINEERING);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════
    // CUR-002: 커리큘럼 등록
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("커리큘럼 등록 end-to-end (CUR-002)")
    class CreateCurriculum {

        @Test
        @DisplayName("TC-005 등록 성공 → step 오름차순 정렬 후 DB 저장")
        void successWithStepSorting() {
            List<CurriculumStepRequest> steps = List.of(
                    makeStep(3, "세번째", "설명3"),
                    makeStep(1, "첫번째", "설명1"),
                    makeStep(2, "두번째", "설명2")
            );

            CurriculumResponse res = curriculumService.createCurriculum(
                    makeCreateRequest(Track.ANALYSIS, steps));

            assertThat(res.getCurriculumSteps()).hasSize(3);
            assertThat(res.getCurriculumSteps().get(0).getStep()).isEqualTo(1);
            assertThat(res.getCurriculumSteps().get(2).getStep()).isEqualTo(3);
            assertThat(curriculumRepository.existsByTrack(Track.ANALYSIS)).isTrue();
        }

        @Test
        @DisplayName("TC-006 track=ALL → INVALID_TRACK_SELECTION")
        void trackAll() {
            assertThatThrownBy(() -> curriculumService.createCurriculum(
                    makeCreateRequest(Track.ALL, List.of(makeStep(1, "제목", "설명")))))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);

            assertThat(curriculumRepository.count()).isZero();
        }

        @Test
        @DisplayName("TC-007 동일 track 중복 등록 → DUPLICATE_TRACK")
        void duplicateTrack() {
            saveCurriculum(Track.ANALYSIS);

            assertThatThrownBy(() -> curriculumService.createCurriculum(
                    makeCreateRequest(Track.ANALYSIS, List.of(makeStep(1, "제목", "설명")))))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_TRACK);

            assertThat(curriculumRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("TC-012 동시에 같은 track으로 등록 요청 → 단 하나만 성공, 나머지는 DUPLICATE_TRACK, DB에는 1건만 남음")
        void concurrentCreateSameTrack() throws Exception {
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
                        curriculumService.createCurriculum(
                                makeCreateRequest(Track.ANALYSIS, List.of(makeStep(1, "제목", "설명"))));
                        successCount.incrementAndGet();
                    } catch (CustomException e) {
                        if (e.getErrorCode() == ErrorCode.DUPLICATE_TRACK) {
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
                assertThat(curriculumRepository.count()).isEqualTo(1);
            } finally {
                // 워커 스레드의 성공한 저장은 메인 테스트 트랜잭션(롤백 대상) 밖에서 독립적으로 커밋되므로,
                // 이 클래스가 공유하는 Spring 컨텍스트/DB에 영구히 남지 않도록 별도 커밋으로 명시적으로 정리한다.
                executor.submit(() -> curriculumRepository.deleteAll()).get(5, TimeUnit.SECONDS);
                executor.shutdown();
            }
        }
    }

    // ══════════════════════════════════════════════
    // CUR-003: 커리큘럼 수정
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("커리큘럼 수정 end-to-end (CUR-003)")
    class UpdateCurriculum {

        @Test
        @DisplayName("TC-008 단계 전체 교체 성공 → 새 단계로 대체, track 유지")
        void success() {
            Curriculum saved = saveCurriculum(Track.ANALYSIS);

            CurriculumResponse res = curriculumService.updateCurriculum(saved.getId(),
                    makeUpdateRequest(List.of(
                            makeStep(1, "교체됨", "새설명1"),
                            makeStep(2, "추가됨", "새설명2")
                    )));

            assertThat(res.getCurriculumSteps()).hasSize(2);
            assertThat(res.getCurriculumSteps().get(0).getTitle()).isEqualTo("교체됨");
            assertThat(res.getTrack()).isEqualTo("ANALYSIS");
        }

        @Test
        @DisplayName("TC-009 존재하지 않는 curriculumId → CURRICULUM_NOT_FOUND")
        void notFound() {
            assertThatThrownBy(() -> curriculumService.updateCurriculum(999L,
                    makeUpdateRequest(List.of(makeStep(1, "제목", "설명")))))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CURRICULUM_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════════════
    // CUR-004: 커리큘럼 삭제
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("커리큘럼 삭제 end-to-end (CUR-004)")
    class DeleteCurriculum {

        @Test
        @DisplayName("TC-010 삭제 성공 → DB에서 제거됨")
        void success() {
            Curriculum saved = saveCurriculum(Track.ANALYSIS);

            curriculumService.deleteCurriculum(saved.getId());

            assertThat(curriculumRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("TC-011 존재하지 않는 curriculumId → CURRICULUM_NOT_FOUND")
        void notFound() {
            assertThatThrownBy(() -> curriculumService.deleteCurriculum(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CURRICULUM_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════════════
    // 생명주기 통합 시나리오
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("커리큘럼 전체 생명주기 시나리오")
    class Lifecycle {

        @Test
        @DisplayName("등록 → 조회 → 수정 → 삭제 흐름이 DB와 일치")
        void fullLifecycle() {
            CurriculumResponse created = curriculumService.createCurriculum(
                    makeCreateRequest(Track.VISUALIZATION, List.of(makeStep(1, "초기", "초기설명"))));
            assertThat(curriculumRepository.count()).isEqualTo(1);

            List<CurriculumResponse> list = curriculumService.getCurriculums(Track.VISUALIZATION);
            assertThat(list).hasSize(1);
            assertThat(list.get(0).getCurriculumSteps().get(0).getTitle()).isEqualTo("초기");

            CurriculumResponse updated = curriculumService.updateCurriculum(created.getId(),
                    makeUpdateRequest(List.of(makeStep(1, "수정됨", "수정설명"))));
            assertThat(updated.getCurriculumSteps().get(0).getTitle()).isEqualTo("수정됨");

            curriculumService.deleteCurriculum(created.getId());
            assertThat(curriculumRepository.findById(created.getId())).isEmpty();
            assertThat(curriculumService.getCurriculums(Track.VISUALIZATION)).isEmpty();
        }
    }
}
