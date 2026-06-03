package com.boaz.backend.domain.curriculum.controller;

import com.boaz.backend.domain.curriculum.dto.response.CurriculumResponse;
import com.boaz.backend.domain.curriculum.dto.response.CurriculumStepResponse;
import com.boaz.backend.domain.curriculum.entity.Curriculum;
import com.boaz.backend.domain.curriculum.service.CurriculumService;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.support.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(
        value = CurriculumController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class CurriculumControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CurriculumService curriculumService;

    private CurriculumResponse makeCurriculumResponse(Long id, Track track) {
        Curriculum c = Curriculum.create(track, "[]");
        ReflectionTestUtils.setField(c, "id", id);
        return CurriculumResponse.from(c, List.of(new CurriculumStepResponse(1, "제목", "설명")));
    }

    // ──────────────────────────────────────────────
    // CUR-001: GET /api/v1/curriculums
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("CUR-001 GET /api/v1/curriculums")
    class GetCurriculums {

        @Test
        @DisplayName("TC-001 track 미입력 → 200 + 전체 목록")
        void allTracks() throws Exception {
            when(curriculumService.getCurriculums(isNull())).thenReturn(List.of(
                    makeCurriculumResponse(1L, Track.ANALYSIS),
                    makeCurriculumResponse(2L, Track.VISUALIZATION)
            ));

            mockMvc.perform(get("/api/v1/curriculums"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("TC-002 track=ANALYSIS → 200 + 해당 트랙만 반환")
        void specificTrack() throws Exception {
            when(curriculumService.getCurriculums(Track.ANALYSIS))
                    .thenReturn(List.of(makeCurriculumResponse(1L, Track.ANALYSIS)));

            mockMvc.perform(get("/api/v1/curriculums")
                            .param("track", "ANALYSIS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].track").value("ANALYSIS"));
        }

        @Test
        @DisplayName("TC-003 track=ALL → 400 INVALID_TRACK_SELECTION")
        void trackAll() throws Exception {
            when(curriculumService.getCurriculums(Track.ALL))
                    .thenThrow(new CustomException(ErrorCode.INVALID_TRACK_SELECTION));

            mockMvc.perform(get("/api/v1/curriculums")
                            .param("track", "ALL"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_TRACK_SELECTION"));
        }

        @Test
        @DisplayName("TC-004 해당 track 데이터 없을 때 → 200 + 빈 배열")
        void noData() throws Exception {
            when(curriculumService.getCurriculums(Track.ENGINEERING)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/curriculums")
                            .param("track", "ENGINEERING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("[Security] 인증 없이 접근 가능 (공개 API)")
        void publicApi() throws Exception {
            when(curriculumService.getCurriculums(any())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/curriculums"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("curriculum_steps 필드명 확인 → snake_case")
        void curriculumStepsFieldName() throws Exception {
            when(curriculumService.getCurriculums(isNull()))
                    .thenReturn(List.of(makeCurriculumResponse(1L, Track.ANALYSIS)));

            mockMvc.perform(get("/api/v1/curriculums"))
                    .andExpect(jsonPath("$.data[0].curriculum_steps").isArray())
                    .andExpect(jsonPath("$.data[0].curriculum_steps[0].step").value(1));
        }
    }
}
