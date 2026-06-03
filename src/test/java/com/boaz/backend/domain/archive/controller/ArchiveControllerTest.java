package com.boaz.backend.domain.archive.controller;

import com.boaz.backend.domain.archive.dto.response.ArchivePageResponse;
import com.boaz.backend.domain.archive.dto.response.ArchiveTermsResponse;
import com.boaz.backend.domain.archive.service.ArchiveService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(
        value = ArchiveController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class ArchiveControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ArchiveService archiveService;

    private ArchivePageResponse mockPageResponse(int currentPage, long totalSize) {
        return ArchivePageResponse.builder()
                .currentPage(currentPage).totalPages(1).size(6)
                .currentSize((int) totalSize).totalSize(totalSize)
                .hasPrevious(false).hasNext(false).posts(List.of())
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ARC-001 GET /api/v1/archiving/projects
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("ARC-001 GET /api/v1/archiving/projects")
    class GetProjects {

        @Test
        @DisplayName("TC-012 인증 없이 기본 파라미터 → 200 OK")
        void getProjects_200() throws Exception {
            when(archiveService.searchArchives(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(mockPageResponse(1, 3));

            mockMvc.perform(get("/api/v1/archiving/projects"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.current_page").value(1))
                    .andExpect(jsonPath("$.data.total_size").value(3));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ARC-002 GET /api/v1/archiving/activities
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("ARC-002 GET /api/v1/archiving/activities")
    class GetActivities {

        @Test
        @DisplayName("TC-011 인증 없이 기본 파라미터 → 200 OK")
        void getActivities_200() throws Exception {
            when(archiveService.searchArchives(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(mockPageResponse(1, 3));

            mockMvc.perform(get("/api/v1/archiving/activities"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.current_page").value(1))
                    .andExpect(jsonPath("$.data.total_size").value(3));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ARC-003 GET /api/v1/archiving/blogs
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("ARC-003 GET /api/v1/archiving/blogs")
    class GetBlogs {

        @Test
        @DisplayName("TC-012 인증 없이 기본 파라미터 → 200 OK")
        void getBlogs_200() throws Exception {
            when(archiveService.searchArchives(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(mockPageResponse(1, 3));

            mockMvc.perform(get("/api/v1/archiving/blogs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.current_page").value(1))
                    .andExpect(jsonPath("$.data.total_size").value(3));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ARC-004 GET /api/v1/archiving/terms
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("ARC-004 GET /api/v1/archiving/terms")
    class GetTerms {

        @Test
        @DisplayName("TC-005 인증 없이 조회 → 200 OK + value/label 검증")
        void getTerms_200() throws Exception {
            when(archiveService.getTerms())
                    .thenReturn(ArchiveTermsResponse.from(List.of(26, 0)));

            mockMvc.perform(get("/api/v1/archiving/terms"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.terms[0].value").value(26))
                    .andExpect(jsonPath("$.data.terms[0].label").value("26기"))
                    .andExpect(jsonPath("$.data.terms[1].value").value(0))
                    .andExpect(jsonPath("$.data.terms[1].label").value("7기 이전"));
        }
    }
}
