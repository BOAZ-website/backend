package com.boaz.backend.domain.archive.controller;

import com.boaz.backend.domain.archive.entity.Archive.Category;
import com.boaz.backend.domain.archive.service.ArchiveAdminService;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.support.TestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ARC-005 ~ ARC-013 아카이빙 등록/수정/삭제 컨트롤러 테스트.
 * 명세: TF 테스트코드 작성 (ARC-005 프로젝트 등록 ~ ARC-013 기술블로그 삭제)
 */
@ActiveProfiles("test")
@WebMvcTest(
        value = ArchiveAdminController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(TestSecurityConfig.class)
class ArchiveAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean ArchiveAdminService archiveAdminService;

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_SUPER")));
    }

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private MockMultipartFile dataPart(Map<String, Object> body) throws Exception {
        return new MockMultipartFile("data", "", "application/json", objectMapper.writeValueAsBytes(body));
    }

    private MockMultipartFile rawDataPart(String raw) {
        return new MockMultipartFile("data", "", "application/json", raw.getBytes());
    }

    private MockMultipartFile imagePart() {
        return new MockMultipartFile("image", "photo.png", "image/png", new byte[]{1, 2, 3});
    }

    private Map<String, Object> validCreateBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("term", 8);
        body.put("title", "AI 수요 예측");
        body.put("track", "ANALYSIS");
        body.put("links", "{\"slideshare\":\"https://slideshare.net/boaz\"}");
        body.put("content_date", "2024-07-01");
        return body;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-005 프로젝트 등록  POST /api/v1/admin/archiving/projects
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-005 POST /api/v1/admin/archiving/projects")
    class CreateProject {

        @Test
        @DisplayName("TC-008 유효한 요청 → 201 Created + body")
        void create_201() throws Exception {
            doNothing().when(archiveAdminService).createArchive(any(), any(), any());

            mockMvc.perform(multipart("/api/v1/admin/archiving/projects")
                            .file(dataPart(validCreateBody()))
                            .file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201));

            verify(archiveAdminService).createArchive(eq(Category.PROJECT), any(), any());
        }

        @Test
        @DisplayName("TC-009 data 파트 누락 → 400 MISSING_PARAMETER")
        void create_missing_data_part() throws Exception {
            mockMvc.perform(multipart("/api/v1/admin/archiving/projects")
                            .file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("MISSING_PARAMETER"));
        }

        @Test
        @DisplayName("TC-009 image 파트 누락 → 400 MISSING_PARAMETER")
        void create_missing_image_part() throws Exception {
            mockMvc.perform(multipart("/api/v1/admin/archiving/projects")
                            .file(dataPart(validCreateBody()))
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("MISSING_PARAMETER"));
        }

        @Test
        @DisplayName("TC-009 Bean Validation 위반(term 누락) → 400 INVALID_INPUT_VALUE")
        void create_bean_validation() throws Exception {
            Map<String, Object> body = validCreateBody();
            body.remove("term");

            mockMvc.perform(multipart("/api/v1/admin/archiving/projects")
                            .file(dataPart(body))
                            .file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"))
                    .andExpect(jsonPath("$.message").value("term을 입력해주세요."));
        }

        @Test
        @DisplayName("TC-009 data 파트 malformed JSON → 400 INVALID_INPUT_VALUE")
        void create_malformed_json() throws Exception {
            mockMvc.perform(multipart("/api/v1/admin/archiving/projects")
                            .file(rawDataPart("{ not json"))
                            .file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-009 data.track에 없는 enum 값 → 400 INVALID_INPUT_VALUE")
        void create_invalid_enum_value() throws Exception {
            Map<String, Object> body = validCreateBody();
            body.put("track", "DEEP_LEARNING");

            mockMvc.perform(multipart("/api/v1/admin/archiving/projects")
                            .file(dataPart(body))
                            .file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-010 토큰 없음 → 401 TOKEN_NOT_FOUND")
        void create_unauthenticated() throws Exception {
            mockMvc.perform(multipart("/api/v1/admin/archiving/projects")
                            .file(dataPart(validCreateBody()))
                            .file(imagePart()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));

            verify(archiveAdminService, never()).createArchive(any(), any(), any());
        }

        @Test
        @DisplayName("TC-010 USER 권한 → 403 ACCESS_DENIED")
        void create_forbidden_for_user() throws Exception {
            mockMvc.perform(multipart("/api/v1/admin/archiving/projects")
                            .file(dataPart(validCreateBody()))
                            .file(imagePart())
                            .with(authentication(userAuth())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));

            verify(archiveAdminService, never()).createArchive(any(), any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-006 활동사진 등록  POST /api/v1/admin/archiving/activities
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-006 POST /api/v1/admin/archiving/activities")
    class CreateActivity {

        @Test
        @DisplayName("TC-012 유효한 요청 → 201 + category=ACTIVITY 전달")
        void create_201() throws Exception {
            Map<String, Object> body = validCreateBody();
            body.put("half", "26-1");
            doNothing().when(archiveAdminService).createArchive(any(), any(), any());

            mockMvc.perform(multipart("/api/v1/admin/archiving/activities")
                            .file(dataPart(body))
                            .file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isCreated());

            verify(archiveAdminService).createArchive(eq(Category.ACTIVITY), any(), any());
        }

        @Test
        @DisplayName("TC-013 half 형식 오류 → 400 INVALID_INPUT_VALUE")
        void create_invalid_half_format() throws Exception {
            Map<String, Object> body = validCreateBody();
            body.put("half", "2026-1");

            mockMvc.perform(multipart("/api/v1/admin/archiving/activities")
                            .file(dataPart(body))
                            .file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"))
                    .andExpect(jsonPath("$.message").value("half 형식이 올바르지 않습니다. (예: 26-1)"));
        }

        @Test
        @DisplayName("TC-016 term 누락 + half 누락 동시 → 400 INVALID_INPUT_VALUE (Bean Validation 선행, MISSING_HALF 아님)")
        void create_missing_term_and_half_bean_validation_first() throws Exception {
            Map<String, Object> body = validCreateBody();
            body.remove("term"); // half 도 없음

            mockMvc.perform(multipart("/api/v1/admin/archiving/activities")
                            .file(dataPart(body))
                            .file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"))
                    .andExpect(jsonPath("$.message").value("term을 입력해주세요."));

            verify(archiveAdminService, never()).createArchive(any(), any(), any());
        }

        @Test
        @DisplayName("TC-009(공통) data 파트 누락 → 400 MISSING_PARAMETER")
        void create_missing_data_part() throws Exception {
            mockMvc.perform(multipart("/api/v1/admin/archiving/activities").file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("MISSING_PARAMETER"));
        }

        @Test
        @DisplayName("TC-009(공통) Bean Validation 위반(title 공백) → 400 INVALID_INPUT_VALUE")
        void create_bean_validation() throws Exception {
            Map<String, Object> body = validCreateBody();
            body.put("half", "26-1");
            body.put("title", "  ");

            mockMvc.perform(multipart("/api/v1/admin/archiving/activities").file(dataPart(body)).file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-009(공통) data 파트 malformed JSON → 400 INVALID_INPUT_VALUE")
        void create_malformed_json() throws Exception {
            mockMvc.perform(multipart("/api/v1/admin/archiving/activities").file(rawDataPart("{ not json")).file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-009(공통) data.track에 없는 enum 값 → 400 INVALID_INPUT_VALUE")
        void create_invalid_enum_value() throws Exception {
            Map<String, Object> body = validCreateBody();
            body.put("half", "26-1");
            body.put("track", "DEEP_LEARNING");

            mockMvc.perform(multipart("/api/v1/admin/archiving/activities").file(dataPart(body)).file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-010(공통) 토큰 없음 → 401 / USER 권한 → 403")
        void create_security() throws Exception {
            Map<String, Object> body = validCreateBody();
            body.put("half", "26-1");

            mockMvc.perform(multipart("/api/v1/admin/archiving/activities").file(dataPart(body)).file(imagePart()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));

            mockMvc.perform(multipart("/api/v1/admin/archiving/activities").file(dataPart(body)).file(imagePart())
                            .with(authentication(userAuth())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));

            verify(archiveAdminService, never()).createArchive(any(), any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-007 기술블로그 등록  POST /api/v1/admin/archiving/blogs
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-007 POST /api/v1/admin/archiving/blogs")
    class CreateBlog {

        @Test
        @DisplayName("TC-012 유효한 요청 → 201 + category=BLOG 전달")
        void create_201() throws Exception {
            doNothing().when(archiveAdminService).createArchive(any(), any(), any());

            mockMvc.perform(multipart("/api/v1/admin/archiving/blogs")
                            .file(dataPart(validCreateBody()))
                            .file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isCreated());

            verify(archiveAdminService).createArchive(eq(Category.BLOG), any(), any());
        }

        @Test
        @DisplayName("TC-009(공통) data 파트 누락 → 400 MISSING_PARAMETER")
        void create_missing_data_part() throws Exception {
            mockMvc.perform(multipart("/api/v1/admin/archiving/blogs").file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("MISSING_PARAMETER"));
        }

        @Test
        @DisplayName("TC-009(공통) Bean Validation 위반(content_date 누락) → 400 INVALID_INPUT_VALUE")
        void create_bean_validation() throws Exception {
            Map<String, Object> body = validCreateBody();
            body.remove("content_date");

            mockMvc.perform(multipart("/api/v1/admin/archiving/blogs").file(dataPart(body)).file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-009(공통) data 파트 malformed JSON → 400 INVALID_INPUT_VALUE")
        void create_malformed_json() throws Exception {
            mockMvc.perform(multipart("/api/v1/admin/archiving/blogs").file(rawDataPart("{ not json")).file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-009(공통) data.track에 없는 enum 값 → 400 INVALID_INPUT_VALUE")
        void create_invalid_enum_value() throws Exception {
            Map<String, Object> body = validCreateBody();
            body.put("track", "DEEP_LEARNING");

            mockMvc.perform(multipart("/api/v1/admin/archiving/blogs").file(dataPart(body)).file(imagePart())
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("TC-010(공통) 토큰 없음 → 401 / USER 권한 → 403")
        void create_security() throws Exception {
            mockMvc.perform(multipart("/api/v1/admin/archiving/blogs").file(dataPart(validCreateBody())).file(imagePart()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));

            mockMvc.perform(multipart("/api/v1/admin/archiving/blogs").file(dataPart(validCreateBody())).file(imagePart())
                            .with(authentication(userAuth())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));

            verify(archiveAdminService, never()).createArchive(any(), any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-008 프로젝트 수정  PATCH /api/v1/admin/archiving/projects/{id}
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-008 PATCH /api/v1/admin/archiving/projects/{id}")
    class UpdateProject {

        @Test
        @DisplayName("TC-011 유효한 요청 → 200 OK + body")
        void update_200() throws Exception {
            doNothing().when(archiveAdminService).updateArchive(any(), any(), any(), any());
            Map<String, Object> body = Map.of("title", "새 제목");

            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/admin/archiving/projects/1")
                            .file(dataPart(body))
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));

            verify(archiveAdminService).updateArchive(eq(Category.PROJECT), eq(1L), any(), any());
        }

        @Test
        @DisplayName("TC-011 존재하지 않는 id → 404 ARCHIVE_NOT_FOUND")
        void update_not_found() throws Exception {
            doThrow(new CustomException(ErrorCode.ARCHIVE_NOT_FOUND))
                    .when(archiveAdminService).updateArchive(any(), any(), any(), any());

            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/admin/archiving/projects/999")
                            .file(dataPart(Map.of("title", "새 제목")))
                            .with(authentication(adminAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("ARCHIVE_NOT_FOUND"));
        }

        @Test
        @DisplayName("TC-011 id 타입 불일치(/projects/abc) → 400 INVALID_PARAMETER_TYPE")
        void update_id_type_mismatch() throws Exception {
            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/admin/archiving/projects/abc")
                            .file(dataPart(Map.of("title", "새 제목")))
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_PARAMETER_TYPE"));
        }

        @Test
        @DisplayName("TC-011 data malformed JSON / 잘못된 enum / half 형식 오류 → 400 INVALID_INPUT_VALUE")
        void update_malformed_or_invalid_enum() throws Exception {
            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/admin/archiving/projects/1")
                            .file(rawDataPart("{ not json"))
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));

            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/admin/archiving/projects/1")
                            .file(dataPart(Map.of("track", "DEEP_LEARNING")))
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"));

            // PROJECT는 half 미사용이나 @Pattern은 여전히 적용됨
            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/admin/archiving/projects/1")
                            .file(dataPart(Map.of("half", "26-3")))
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"))
                    .andExpect(jsonPath("$.message").value("half 형식이 올바르지 않습니다. (예: 26-1)"));
        }

        @Test
        @DisplayName("TC-011 토큰 없음 → 401 TOKEN_NOT_FOUND / USER 권한 → 403 ACCESS_DENIED")
        void update_security() throws Exception {
            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/admin/archiving/projects/1")
                            .file(dataPart(Map.of("title", "새 제목"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));

            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/admin/archiving/projects/1")
                            .file(dataPart(Map.of("title", "새 제목")))
                            .with(authentication(userAuth())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));

            verify(archiveAdminService, never()).updateArchive(any(), any(), any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-009 / ARC-010 활동사진·기술블로그 수정 (category 전달 검증)
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-009 / ARC-010 PATCH activities|blogs")
    class UpdateActivityAndBlog {

        @Test
        @DisplayName("ARC-009 TC-004 PATCH /activities/{id} → category=ACTIVITY 전달")
        void update_activity() throws Exception {
            doNothing().when(archiveAdminService).updateArchive(any(), any(), any(), any());

            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/admin/archiving/activities/1")
                            .file(dataPart(Map.of("title", "새 제목")))
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk());

            verify(archiveAdminService).updateArchive(eq(Category.ACTIVITY), eq(1L), any(), any());
        }

        @Test
        @DisplayName("ARC-009 TC-004 PATCH /activities half 형식 오류 → 400 INVALID_INPUT_VALUE")
        void update_activity_invalid_half() throws Exception {
            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/admin/archiving/activities/1")
                            .file(dataPart(Map.of("half", "26-3")))
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_INPUT_VALUE"))
                    .andExpect(jsonPath("$.message").value("half 형식이 올바르지 않습니다. (예: 26-1)"));
        }

        @Test
        @DisplayName("ARC-010 PATCH /blogs/{id} → category=BLOG 전달")
        void update_blog() throws Exception {
            doNothing().when(archiveAdminService).updateArchive(any(), any(), any(), any());

            mockMvc.perform(multipart(HttpMethod.PATCH, "/api/v1/admin/archiving/blogs/1")
                            .file(dataPart(Map.of("title", "새 제목")))
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk());

            verify(archiveAdminService).updateArchive(eq(Category.BLOG), eq(1L), any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-011 프로젝트 삭제  DELETE /api/v1/admin/archiving/projects/{id}
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-011 DELETE /api/v1/admin/archiving/projects/{id}")
    class DeleteProject {

        @Test
        @DisplayName("TC-004 유효한 요청 → 200 OK + body")
        void delete_200() throws Exception {
            doNothing().when(archiveAdminService).deleteArchive(any(), any());

            mockMvc.perform(delete("/api/v1/admin/archiving/projects/1")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));

            verify(archiveAdminService).deleteArchive(Category.PROJECT, 1L);
        }

        @Test
        @DisplayName("TC-004 존재하지 않는 id → 404 ARCHIVE_NOT_FOUND")
        void delete_not_found() throws Exception {
            doThrow(new CustomException(ErrorCode.ARCHIVE_NOT_FOUND))
                    .when(archiveAdminService).deleteArchive(any(), any());

            mockMvc.perform(delete("/api/v1/admin/archiving/projects/999")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("ARCHIVE_NOT_FOUND"));
        }

        @Test
        @DisplayName("TC-004 category 불일치 → 400 UNSUPPORTED_ARCHIVE_CATEGORY")
        void delete_category_mismatch() throws Exception {
            doThrow(new CustomException(ErrorCode.UNSUPPORTED_ARCHIVE_CATEGORY))
                    .when(archiveAdminService).deleteArchive(any(), any());

            mockMvc.perform(delete("/api/v1/admin/archiving/projects/1")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("UNSUPPORTED_ARCHIVE_CATEGORY"));
        }

        @Test
        @DisplayName("TC-004 id 타입 불일치(/projects/abc) → 400 INVALID_PARAMETER_TYPE")
        void delete_id_type_mismatch() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/archiving/projects/abc")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error_code").value("INVALID_PARAMETER_TYPE"));
        }

        @Test
        @DisplayName("TC-004 토큰 없음 → 401 / USER 권한 → 403")
        void delete_security() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/archiving/projects/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("TOKEN_NOT_FOUND"));

            mockMvc.perform(delete("/api/v1/admin/archiving/projects/1")
                            .with(authentication(userAuth())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("ACCESS_DENIED"));

            verify(archiveAdminService, never()).deleteArchive(any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-012 / ARC-013 활동사진·기술블로그 삭제 (category 전달 검증)
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-012 / ARC-013 DELETE activities|blogs")
    class DeleteActivityAndBlog {

        @Test
        @DisplayName("ARC-012 DELETE /activities/{id} → category=ACTIVITY 전달")
        void delete_activity() throws Exception {
            doNothing().when(archiveAdminService).deleteArchive(any(), any());

            mockMvc.perform(delete("/api/v1/admin/archiving/activities/1")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk());

            verify(archiveAdminService).deleteArchive(Category.ACTIVITY, 1L);
        }

        @Test
        @DisplayName("ARC-013 DELETE /blogs/{id} → category=BLOG 전달")
        void delete_blog() throws Exception {
            doNothing().when(archiveAdminService).deleteArchive(any(), any());

            mockMvc.perform(delete("/api/v1/admin/archiving/blogs/1")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isOk());

            verify(archiveAdminService).deleteArchive(Category.BLOG, 1L);
        }
    }
}
