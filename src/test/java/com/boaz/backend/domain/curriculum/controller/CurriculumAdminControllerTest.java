package com.boaz.backend.domain.curriculum.controller;

import com.boaz.backend.domain.curriculum.dto.response.CurriculumResponse;
import com.boaz.backend.domain.curriculum.dto.response.CurriculumStepResponse;
import com.boaz.backend.domain.curriculum.entity.Curriculum;
import com.boaz.backend.domain.curriculum.service.CurriculumService;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.support.TestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest(
        value = CurriculumAdminController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class CurriculumAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean CurriculumService curriculumService;

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER")));
    }

    private CurriculumResponse makeCurriculumResponse(Long id, Track track) {
        Curriculum c = Curriculum.create(track, "[]");
        ReflectionTestUtils.setField(c, "id", id);
        return CurriculumResponse.from(c, List.of(new CurriculumStepResponse(1, "제목", "설명")));
    }

    private String createBody(String track) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("track", track);
        ArrayNode steps = body.putArray("curriculum_steps");
        steps.addObject().put("step", 1).put("title", "제목").put("desc", "설명");
        return body.toString();
    }

    private String updateBody() {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode steps = body.putArray("curriculum_steps");
        steps.addObject().put("step", 1).put("title", "교체됨").put("desc", "새설명");
        return body.toString();
    }

    // ──────────────────────────────────────────────
    // CUR-002: POST /api/v1/admin/curriculums
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("CUR-002 POST /api/v1/admin/curriculums")
    class CreateCurriculum {

        @Test
        @DisplayName("TC-005 등록 성공 → 201 Created")
        void success() throws Exception {
            when(curriculumService.createCurriculum(any()))
                    .thenReturn(makeCurriculumResponse(1L, Track.ANALYSIS));

            mockMvc.perform(post("/api/v1/admin/curriculums")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("ANALYSIS")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.track").value("ANALYSIS"));
        }

        @Test
        @DisplayName("TC-006 track=ALL → 400 INVALID_TRACK_SELECTION")
        void trackAll() throws Exception {
            when(curriculumService.createCurriculum(any()))
                    .thenThrow(new CustomException(ErrorCode.INVALID_TRACK_SELECTION));

            mockMvc.perform(post("/api/v1/admin/curriculums")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("ALL")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_TRACK_SELECTION"));
        }

        @Test
        @DisplayName("TC-007 동일 track 중복 등록 → 409 DUPLICATE_TRACK")
        void duplicateTrack() throws Exception {
            when(curriculumService.createCurriculum(any()))
                    .thenThrow(new CustomException(ErrorCode.DUPLICATE_TRACK));

            mockMvc.perform(post("/api/v1/admin/curriculums")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("ANALYSIS")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error_code").value("DUPLICATE_TRACK"));
        }

        @Test
        @DisplayName("curriculum_steps 누락 → 400")
        void missingSteps() throws Exception {
            String body = objectMapper.createObjectNode().put("track", "ANALYSIS").toString();

            mockMvc.perform(post("/api/v1/admin/curriculums")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/admin/curriculums")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody("ANALYSIS")))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ──────────────────────────────────────────────
    // CUR-003: PUT /api/v1/admin/curriculums/{id}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("CUR-003 PUT /api/v1/admin/curriculums/{curriculumId}")
    class UpdateCurriculum {

        @Test
        @DisplayName("TC-008 단계 전체 교체 성공 → 200 OK")
        void success() throws Exception {
            when(curriculumService.updateCurriculum(eq(1L), any()))
                    .thenReturn(makeCurriculumResponse(1L, Track.ANALYSIS));

            mockMvc.perform(put("/api/v1/admin/curriculums/1")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("TC-009 존재하지 않는 curriculumId → 404 CURRICULUM_NOT_FOUND")
        void notFound() throws Exception {
            when(curriculumService.updateCurriculum(eq(999L), any()))
                    .thenThrow(new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

            mockMvc.perform(put("/api/v1/admin/curriculums/999")
                            .with(authentication(adminAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("CURRICULUM_NOT_FOUND"));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(put("/api/v1/admin/curriculums/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ──────────────────────────────────────────────
    // CUR-004: DELETE /api/v1/admin/curriculums/{id}
    // ──────────────────────────────────────────────
    @Nested
    @DisplayName("CUR-004 DELETE /api/v1/admin/curriculums/{curriculumId}")
    class DeleteCurriculum {

        @Test
        @DisplayName("TC-010 삭제 성공 → 200 OK")
        void success() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/curriculums/1")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("TC-011 존재하지 않는 curriculumId → 404 CURRICULUM_NOT_FOUND")
        void notFound() throws Exception {
            doThrow(new CustomException(ErrorCode.CURRICULUM_NOT_FOUND))
                    .when(curriculumService).deleteCurriculum(999L);

            mockMvc.perform(delete("/api/v1/admin/curriculums/999")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("CURRICULUM_NOT_FOUND"));
        }

        @Test
        @DisplayName("[Security] 미인증 요청 → 401")
        void unauthorized() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/curriculums/1"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
