package com.boaz.backend.domain.archive.integration;

import com.boaz.backend.domain.archive.dto.request.ArchiveCreateRequest;
import com.boaz.backend.domain.archive.dto.request.ArchiveUpdateRequest;
import com.boaz.backend.domain.archive.entity.Archive;
import com.boaz.backend.domain.archive.entity.Archive.Category;
import com.boaz.backend.domain.archive.repository.ArchiveRepository;
import com.boaz.backend.domain.archive.service.ArchiveAdminService;
import com.boaz.backend.domain.archive.service.ArchiveService;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.util.S3Service;
import com.boaz.backend.support.TestcontainersBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ARC-005 ~ ARC-013 아카이빙 등록/수정/삭제 통합 테스트 (Testcontainers MySQL).
 * S3Service 는 mock 처리하며, 커밋 후 콜백(afterCommit)을 검증하기 위해 각 시나리오를
 * TransactionTemplate 로 실제 커밋한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ArchiveAdminIntegrationTest extends TestcontainersBase {

    @Autowired ArchiveAdminService archiveAdminService;
    @Autowired ArchiveService archiveService;
    @Autowired ArchiveRepository archiveRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @MockitoBean S3Service s3Service;

    private TransactionTemplate tx;

    private static final String BUCKET = "test-archiving-bucket";

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        archiveRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        archiveRepository.deleteAll();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────

    private ArchiveCreateRequest createRequest(String title, Track track, String half) {
        ArchiveCreateRequest r = new ArchiveCreateRequest();
        ReflectionTestUtils.setField(r, "term", 26);
        ReflectionTestUtils.setField(r, "title", title);
        ReflectionTestUtils.setField(r, "teamName", "팀보아즈");
        ReflectionTestUtils.setField(r, "track", track);
        ReflectionTestUtils.setField(r, "links", "{\"velog\":\"https://velog.io/x\"}");
        ReflectionTestUtils.setField(r, "contentDate", LocalDate.of(2024, 7, 1));
        ReflectionTestUtils.setField(r, "half", half);
        return r;
    }

    private MultipartFile image() {
        return new MockMultipartFile("image", "photo.png", "image/png", new byte[]{1, 2, 3});
    }

    private Archive save(Category category, String title, String imageUrl) {
        return archiveRepository.save(Archive.builder()
                .term(26).category(category).title(title).teamName("팀A").track(Track.ANALYSIS)
                .imageUrl(imageUrl).links("{}").contentDate(LocalDate.of(2024, 1, 1))
                .build());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-005 프로젝트 등록
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-005 프로젝트 등록 통합")
    class CreateProject {

        @Test
        @DisplayName("TC-I-001 정상 등록 → archive 테이블에 1행 저장")
        void create_persists_row() {
            when(s3Service.uploadImage(any(), any(), any())).thenReturn("https://bucket/projects/26기/분석/x.png");

            tx.executeWithoutResult(s ->
                    archiveAdminService.createArchive(Category.PROJECT, createRequest("실시간 추천 시스템", Track.ANALYSIS, null), image()));

            assertThat(archiveRepository.count()).isEqualTo(1);
            Archive saved = archiveRepository.findAll().get(0);
            assertThat(saved.getCategory()).isEqualTo(Category.PROJECT);
            assertThat(saved.getTitle()).isEqualTo("실시간 추천 시스템");
            assertThat(saved.getImageUrl()).isEqualTo("https://bucket/projects/26기/분석/x.png");
        }

        @Test
        @DisplayName("TC-I-002 DB 제약 위반(title 255 초과) → 롤백 + 업로드 이미지 S3 삭제")
        void create_db_constraint_rollback_cleans_s3() {
            String uploaded = "https://bucket/projects/26기/분석/x.png";
            when(s3Service.uploadImage(any(), any(), any())).thenReturn(uploaded);
            String tooLongTitle = "가".repeat(256);

            assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                    archiveAdminService.createArchive(Category.PROJECT, createRequest(tooLongTitle, Track.ANALYSIS, null), image())))
                    .isInstanceOf(CustomException.class);

            assertThat(archiveRepository.count()).isZero();
            verify(s3Service).deleteImage(BUCKET, uploaded);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-006 활동사진 등록
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-006 활동사진 등록 통합")
    class CreateActivity {

        @Test
        @DisplayName("TC-I-001 track=ALL 활동사진 저장 성공")
        void create_activity_track_all() {
            when(s3Service.uploadImage(any(), any(), any())).thenReturn("https://bucket/activities/26기/26-1/전체/x.png");

            tx.executeWithoutResult(s ->
                    archiveAdminService.createArchive(Category.ACTIVITY, createRequest("전체 세미나", Track.ALL, "26-1"), image()));

            assertThat(archiveRepository.count()).isEqualTo(1);
            assertThat(archiveRepository.findAll().get(0).getTrack()).isEqualTo(Track.ALL);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-007 기술블로그 등록 (ARC-005 TC-I-001/002 를 category=BLOG 로 동일 적용)
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-007 기술블로그 등록 통합")
    class CreateBlog {

        @Test
        @DisplayName("TC-I-001 정상 등록 → archive 테이블에 category=BLOG 1행 저장")
        void create_persists_row() {
            when(s3Service.uploadImage(any(), any(), any())).thenReturn("https://bucket/blogs/26기/엔지니어링/x.png");

            tx.executeWithoutResult(s ->
                    archiveAdminService.createArchive(Category.BLOG, createRequest("Transformer 구현기", Track.ENGINEERING, null), image()));

            assertThat(archiveRepository.count()).isEqualTo(1);
            assertThat(archiveRepository.findAll().get(0).getCategory()).isEqualTo(Category.BLOG);
        }

        @Test
        @DisplayName("TC-I-002 DB 제약 위반(title 255 초과) → 롤백 + 업로드 이미지 S3 삭제")
        void create_db_constraint_rollback_cleans_s3() {
            String uploaded = "https://bucket/blogs/26기/엔지니어링/x.png";
            when(s3Service.uploadImage(any(), any(), any())).thenReturn(uploaded);

            assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                    archiveAdminService.createArchive(Category.BLOG, createRequest("가".repeat(256), Track.ENGINEERING, null), image())))
                    .isInstanceOf(CustomException.class);

            assertThat(archiveRepository.count()).isZero();
            verify(s3Service).deleteImage(BUCKET, uploaded);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-008 프로젝트 수정
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-008 프로젝트 수정 통합")
    class UpdateProject {

        @Test
        @DisplayName("TC-I-001 부분 수정 후 DB 반영 + teamName/contentDate 유지")
        void partial_update_keeps_other_fields() {
            Archive a = save(Category.PROJECT, "old", "https://bucket/old.png");
            ArchiveUpdateRequest request = new ArchiveUpdateRequest();
            ReflectionTestUtils.setField(request, "title", "new");

            tx.executeWithoutResult(s -> archiveAdminService.updateArchive(Category.PROJECT, a.getId(), request, null));

            Archive reloaded = archiveRepository.findById(a.getId()).orElseThrow();
            assertThat(reloaded.getTitle()).isEqualTo("new");
            assertThat(reloaded.getTeamName()).isEqualTo("팀A");
            assertThat(reloaded.getContentDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        }

        @Test
        @DisplayName("TC-I-002 이미지 교체 → 커밋 후 기존 이미지 S3 삭제")
        void image_replace_deletes_old_after_commit() {
            Archive a = save(Category.PROJECT, "제목", "https://bucket/old.png");
            when(s3Service.uploadImage(any(), any(), any())).thenReturn("https://bucket/new.png");

            tx.executeWithoutResult(s ->
                    archiveAdminService.updateArchive(Category.PROJECT, a.getId(), new ArchiveUpdateRequest(), image()));

            assertThat(archiveRepository.findById(a.getId()).orElseThrow().getImageUrl())
                    .isEqualTo("https://bucket/new.png");
            verify(s3Service).deleteImage(BUCKET, "https://bucket/old.png");
        }

        @Test
        @DisplayName("TC-I-003 flush 실패(title 255 초과) → 롤백 + 새 이미지 삭제")
        void update_db_constraint_rollback_cleans_new_image() {
            Archive a = save(Category.PROJECT, "제목", "https://bucket/old.png");
            when(s3Service.uploadImage(any(), any(), any())).thenReturn("https://bucket/new.png");
            ArchiveUpdateRequest request = new ArchiveUpdateRequest();
            ReflectionTestUtils.setField(request, "title", "가".repeat(256));

            assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                    archiveAdminService.updateArchive(Category.PROJECT, a.getId(), request, image())))
                    .isInstanceOf(CustomException.class);

            assertThat(archiveRepository.findById(a.getId()).orElseThrow().getTitle()).isEqualTo("제목");
            verify(s3Service).deleteImage(BUCKET, "https://bucket/new.png");
            verify(s3Service, never()).deleteImage(BUCKET, "https://bucket/old.png");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-011 프로젝트 삭제
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-011 프로젝트 삭제 통합")
    class DeleteProject {

        @Test
        @DisplayName("TC-I-001 삭제 후 DB row 제거 + 커밋 후 S3 삭제")
        void delete_removes_row_and_deletes_s3_after_commit() {
            Archive a = save(Category.PROJECT, "제목", "https://bucket/p.png");

            tx.executeWithoutResult(s -> archiveAdminService.deleteArchive(Category.PROJECT, a.getId()));

            assertThat(archiveRepository.findById(a.getId())).isEmpty();
            verify(s3Service).deleteImage(BUCKET, "https://bucket/p.png");
        }

        @Test
        @DisplayName("TC-I-002 롤백 시 DB row 유지 + S3 삭제 미호출")
        void delete_rollback_keeps_row_no_s3() {
            Archive a = save(Category.PROJECT, "제목", "https://bucket/p.png");

            tx.executeWithoutResult(s -> {
                archiveAdminService.deleteArchive(Category.PROJECT, a.getId());
                s.setRollbackOnly();
            });

            assertThat(archiveRepository.findById(a.getId())).isPresent();
            verify(s3Service, never()).deleteImage(any(), any());
        }

        @Test
        @DisplayName("TC-003 비고: afterCommit S3 삭제 실패해도 예외 미전파 + DB 삭제 유지")
        void delete_afterCommit_s3_failure_does_not_propagate() {
            Archive a = save(Category.PROJECT, "제목", "https://bucket/p.png");
            doThrow(new CustomException(ErrorCode.S3_DELETE_FAILED))
                    .when(s3Service).deleteImage(BUCKET, "https://bucket/p.png");

            tx.executeWithoutResult(s -> archiveAdminService.deleteArchive(Category.PROJECT, a.getId()));

            assertThat(archiveRepository.findById(a.getId())).isEmpty();
            verify(s3Service).deleteImage(BUCKET, "https://bucket/p.png");
        }

        @Test
        @DisplayName("TC-I-003 삭제 후 조회 API(GET /projects)에서 미노출")
        void deleted_not_visible_in_search() {
            Archive keep = save(Category.PROJECT, "남는 프로젝트", "https://bucket/a.png");
            Archive gone = save(Category.PROJECT, "삭제될 프로젝트", "https://bucket/b.png");

            tx.executeWithoutResult(s -> archiveAdminService.deleteArchive(Category.PROJECT, gone.getId()));

            var response = archiveService.searchArchives(Category.PROJECT, Track.ALL, null, null, 1, 10);
            assertThat(response.getTotalSize()).isEqualTo(1);
            assertThat(response.getPosts().get(0).getId()).isEqualTo(keep.getId());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-012 / ARC-013 활동사진·기술블로그 삭제
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-012 / ARC-013 삭제 통합")
    class DeleteActivityAndBlog {

        @Test
        @DisplayName("ARC-012 TC-001 category 불일치(id가 PROJECT) → UNSUPPORTED_ARCHIVE_CATEGORY, row 유지")
        void delete_activity_category_mismatch() {
            Archive a = save(Category.PROJECT, "제목", "https://bucket/p.png");

            assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                    archiveAdminService.deleteArchive(Category.ACTIVITY, a.getId())))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_ARCHIVE_CATEGORY);

            assertThat(archiveRepository.findById(a.getId())).isPresent();
        }

        @Test
        @DisplayName("ARC-012 TC-I-002 삭제 후 GET /activities 미노출")
        void deleted_activity_not_visible() {
            Archive keep = save(Category.ACTIVITY, "남는 활동", "https://bucket/a.png");
            Archive gone = save(Category.ACTIVITY, "삭제될 활동", "https://bucket/b.png");

            tx.executeWithoutResult(s -> archiveAdminService.deleteArchive(Category.ACTIVITY, gone.getId()));

            var response = archiveService.searchArchives(Category.ACTIVITY, null, null, null, 1, 10);
            assertThat(response.getTotalSize()).isEqualTo(1);
            assertThat(response.getPosts().get(0).getId()).isEqualTo(keep.getId());
        }

        @Test
        @DisplayName("ARC-013 TC-I-002 삭제 후 GET /blogs 미노출")
        void deleted_blog_not_visible() {
            Archive keep = save(Category.BLOG, "남는 블로그", "https://bucket/a.png");
            Archive gone = save(Category.BLOG, "삭제될 블로그", "https://bucket/b.png");

            tx.executeWithoutResult(s -> archiveAdminService.deleteArchive(Category.BLOG, gone.getId()));

            var response = archiveService.searchArchives(Category.BLOG, Track.ALL, null, null, 1, 10);
            assertThat(response.getTotalSize()).isEqualTo(1);
            assertThat(response.getPosts().get(0).getId()).isEqualTo(keep.getId());
        }
    }
}
