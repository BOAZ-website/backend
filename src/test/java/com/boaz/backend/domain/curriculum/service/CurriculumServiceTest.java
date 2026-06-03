package com.boaz.backend.domain.curriculum.service;

import com.boaz.backend.domain.curriculum.dto.request.CurriculumCreateRequest;
import com.boaz.backend.domain.curriculum.dto.request.CurriculumStepRequest;
import com.boaz.backend.domain.curriculum.dto.request.CurriculumUpdateRequest;
import com.boaz.backend.domain.curriculum.dto.response.CurriculumResponse;
import com.boaz.backend.domain.curriculum.entity.Curriculum;
import com.boaz.backend.domain.curriculum.repository.CurriculumRepository;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurriculumServiceTest {

    @InjectMocks CurriculumService curriculumService;
    @Mock CurriculumRepository curriculumRepository;
    @Spy ObjectMapper objectMapper;

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private Curriculum makeCurriculum(Long id, Track track) {
        String stepsJson = "[{\"step\":1,\"title\":\"제목\",\"desc\":\"설명\"}]";
        Curriculum c = Curriculum.create(track, stepsJson);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
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
    @DisplayName("CUR-001 커리큘럼 조회")
    class GetCurriculums {

        @Test
        @DisplayName("TC-001 track 미입력 → 전체 반환")
        void allTracks() {
            when(curriculumRepository.findAll()).thenReturn(List.of(
                    makeCurriculum(1L, Track.ANALYSIS),
                    makeCurriculum(2L, Track.VISUALIZATION)
            ));

            List<CurriculumResponse> result = curriculumService.getCurriculums(null);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("TC-002 track=ANALYSIS → 해당 트랙만 반환")
        void specificTrack() {
            Curriculum c = makeCurriculum(1L, Track.ANALYSIS);
            when(curriculumRepository.findByTrack(Track.ANALYSIS)).thenReturn(Optional.of(c));

            List<CurriculumResponse> result = curriculumService.getCurriculums(Track.ANALYSIS);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTrack()).isEqualTo("ANALYSIS");
        }

        @Test
        @DisplayName("TC-003 track=ALL → INVALID_TRACK_SELECTION")
        void trackAll() {
            assertThatThrownBy(() -> curriculumService.getCurriculums(Track.ALL))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);
        }

        @Test
        @DisplayName("TC-004 해당 track 데이터 없음 → 빈 배열 반환")
        void noData() {
            when(curriculumRepository.findByTrack(Track.ENGINEERING)).thenReturn(Optional.empty());

            List<CurriculumResponse> result = curriculumService.getCurriculums(Track.ENGINEERING);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════
    // CUR-002: 커리큘럼 등록
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("CUR-002 커리큘럼 등록")
    class CreateCurriculum {

        @Test
        @DisplayName("TC-005 등록 성공 → step 오름차순 정렬 후 저장")
        void successWithStepSorting() {
            when(curriculumRepository.existsByTrack(Track.ANALYSIS)).thenReturn(false);
            when(curriculumRepository.save(any())).thenAnswer(inv -> {
                Curriculum c = inv.getArgument(0);
                ReflectionTestUtils.setField(c, "id", 1L);
                return c;
            });

            List<CurriculumStepRequest> steps = List.of(
                    makeStep(3, "세번째", "설명3"),
                    makeStep(1, "첫번째", "설명1"),
                    makeStep(2, "두번째", "설명2")
            );

            CurriculumResponse res = curriculumService.createCurriculum(
                    makeCreateRequest(Track.ANALYSIS, steps));

            assertThat(res.getCurriculumSteps()).hasSize(3);
            assertThat(res.getCurriculumSteps().get(0).getStep()).isEqualTo(1);
            assertThat(res.getCurriculumSteps().get(1).getStep()).isEqualTo(2);
            assertThat(res.getCurriculumSteps().get(2).getStep()).isEqualTo(3);
            verify(curriculumRepository).save(any(Curriculum.class));
        }

        @Test
        @DisplayName("TC-006 track=ALL → INVALID_TRACK_SELECTION, 저장 안 함")
        void trackAll() {
            assertThatThrownBy(() -> curriculumService.createCurriculum(
                    makeCreateRequest(Track.ALL, List.of(makeStep(1, "제목", "설명")))))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);

            verify(curriculumRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-007 동일 track 중복 등록 → DUPLICATE_TRACK, 저장 안 함")
        void duplicateTrack() {
            when(curriculumRepository.existsByTrack(Track.ANALYSIS)).thenReturn(true);

            assertThatThrownBy(() -> curriculumService.createCurriculum(
                    makeCreateRequest(Track.ANALYSIS, List.of(makeStep(1, "제목", "설명")))))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_TRACK);

            verify(curriculumRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════
    // CUR-003: 커리큘럼 수정
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("CUR-003 커리큘럼 수정")
    class UpdateCurriculum {

        @Test
        @DisplayName("TC-008 단계 전체 교체 성공 → track 유지")
        void success() {
            Curriculum c = makeCurriculum(1L, Track.ANALYSIS);
            when(curriculumRepository.findById(1L)).thenReturn(Optional.of(c));

            CurriculumResponse res = curriculumService.updateCurriculum(1L,
                    makeUpdateRequest(List.of(makeStep(1, "교체됨", "새설명"), makeStep(2, "추가됨", "설명2"))));

            assertThat(res.getCurriculumSteps()).hasSize(2);
            assertThat(res.getCurriculumSteps().get(0).getTitle()).isEqualTo("교체됨");
            assertThat(res.getTrack()).isEqualTo("ANALYSIS");
        }

        @Test
        @DisplayName("TC-009 존재하지 않는 curriculumId → CURRICULUM_NOT_FOUND")
        void notFound() {
            when(curriculumRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> curriculumService.updateCurriculum(999L,
                    makeUpdateRequest(List.of(makeStep(1, "제목", "설명")))))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CURRICULUM_NOT_FOUND);
        }
    }

    // ══════════════════════════════════════════════
    // CUR-004: 커리큘럼 삭제
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("CUR-004 커리큘럼 삭제")
    class DeleteCurriculum {

        @Test
        @DisplayName("TC-010 삭제 성공 → repository.delete 호출")
        void success() {
            Curriculum c = makeCurriculum(1L, Track.ANALYSIS);
            when(curriculumRepository.findById(1L)).thenReturn(Optional.of(c));

            curriculumService.deleteCurriculum(1L);

            verify(curriculumRepository).delete(c);
        }

        @Test
        @DisplayName("TC-011 존재하지 않는 curriculumId → CURRICULUM_NOT_FOUND, 삭제 안 함")
        void notFound() {
            when(curriculumRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> curriculumService.deleteCurriculum(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CURRICULUM_NOT_FOUND);

            verify(curriculumRepository, never()).delete(any());
        }
    }
}
