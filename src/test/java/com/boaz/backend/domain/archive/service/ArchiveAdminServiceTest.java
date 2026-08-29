package com.boaz.backend.domain.archive.service;

import com.boaz.backend.domain.archive.dto.request.ArchiveCreateRequest;
import com.boaz.backend.domain.archive.dto.request.ArchiveUpdateRequest;
import com.boaz.backend.domain.archive.entity.Archive;
import com.boaz.backend.domain.archive.entity.Archive.Category;
import com.boaz.backend.domain.archive.repository.ArchiveRepository;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import com.boaz.backend.global.util.S3Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ARC-005 ~ ARC-013 아카이빙 등록/수정/삭제 서비스 단위 테스트.
 * 명세: TF 테스트코드 작성 (ARC-005 프로젝트 등록 ~ ARC-013 기술블로그 삭제)
 */
@ExtendWith(MockitoExtension.class)
class ArchiveAdminServiceTest {

    @InjectMocks ArchiveAdminService archiveAdminService;
    @Mock ArchiveRepository archiveRepository;
    @Mock S3Service s3Service;

    private static final String BUCKET = "test-archiving-bucket";
    private static final String NEW_IMAGE_URL =
            "https://test-archiving-bucket.s3.ap-northeast-2.amazonaws.com/projects/8기/분석/new-250701120000.png";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(archiveAdminService, "archivingBucket", BUCKET);
        // 성공 경로가 registerS3RollbackCleanup / registerS3DeleteAfterCommit 를 호출하므로 동기화 활성화
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────

    private ArchiveCreateRequest createRequest(Integer term, String title, String teamName, Track track,
                                               String links, LocalDate contentDate, String half) {
        ArchiveCreateRequest r = new ArchiveCreateRequest();
        ReflectionTestUtils.setField(r, "term", term);
        ReflectionTestUtils.setField(r, "title", title);
        ReflectionTestUtils.setField(r, "teamName", teamName);
        ReflectionTestUtils.setField(r, "track", track);
        ReflectionTestUtils.setField(r, "links", links);
        ReflectionTestUtils.setField(r, "contentDate", contentDate);
        ReflectionTestUtils.setField(r, "half", half);
        return r;
    }

    private ArchiveCreateRequest validProjectRequest() {
        return createRequest(8, "AI 수요 예측", "팀보아즈", Track.ANALYSIS,
                "{\"slideshare\":\"https://slideshare.net/boaz\"}", LocalDate.of(2024, 7, 1), null);
    }

    private ArchiveUpdateRequest updateRequest() {
        return new ArchiveUpdateRequest(); // term/title/track/links=null, teamName/contentDate=undefined, half=null
    }

    private MockMultipartFile image(String filename) {
        return new MockMultipartFile("image", filename, "image/png", new byte[]{1, 2, 3});
    }

    private MockMultipartFile validImage() {
        return image("photo.png");
    }

    private MultipartFile sizedImage(long size) {
        MultipartFile m = mock(MultipartFile.class);
        lenient().when(m.isEmpty()).thenReturn(false);
        lenient().when(m.getSize()).thenReturn(size);
        lenient().when(m.getOriginalFilename()).thenReturn("photo.png");
        return m;
    }

    private Archive archive(Category category, String imageUrl) {
        return Archive.builder()
                .term(8).category(category).title("old title").teamName("팀A").track(Track.ANALYSIS)
                .imageUrl(imageUrl).links("{\"a\":\"https://a.com\"}").contentDate(LocalDate.of(2024, 1, 1))
                .build();
    }

    private void triggerAfterCommit() {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-005 프로젝트 등록
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-005 프로젝트 등록")
    class CreateProject {

        @Test
        @DisplayName("TC-001 유효한 요청/이미지 → save 호출 + 필드 매핑 + S3 key 규칙")
        void create_success() {
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            when(s3Service.uploadImage(eq(BUCKET), keyCaptor.capture(), any())).thenReturn(NEW_IMAGE_URL);

            archiveAdminService.createArchive(Category.PROJECT, validProjectRequest(), validImage());

            ArgumentCaptor<Archive> saved = ArgumentCaptor.forClass(Archive.class);
            verify(archiveRepository).save(saved.capture());
            verify(archiveRepository).flush();

            Archive a = saved.getValue();
            assertThat(a.getCategory()).isEqualTo(Category.PROJECT);
            assertThat(a.getTerm()).isEqualTo(8);
            assertThat(a.getTitle()).isEqualTo("AI 수요 예측");
            assertThat(a.getTrack()).isEqualTo(Track.ANALYSIS);
            assertThat(a.getImageUrl()).isEqualTo(NEW_IMAGE_URL);
            assertThat(a.getContentDate()).isEqualTo(LocalDate.of(2024, 7, 1));
            assertThat(keyCaptor.getValue()).matches("^projects/8기/분석/AI-수요-예측-\\d{12}\\.png$");
        }

        @Test
        @DisplayName("TC-002 teamName null / links {} 도 등록 성공")
        void create_optional_values() {
            ArchiveCreateRequest request = createRequest(8, "제목", null, Track.ANALYSIS,
                    "{}", LocalDate.of(2024, 7, 1), null);
            when(s3Service.uploadImage(any(), any(), any())).thenReturn(NEW_IMAGE_URL);

            archiveAdminService.createArchive(Category.PROJECT, request, validImage());

            ArgumentCaptor<Archive> saved = ArgumentCaptor.forClass(Archive.class);
            verify(archiveRepository).save(saved.capture());
            assertThat(saved.getValue().getTeamName()).isNull();
        }

        @Test
        @DisplayName("TC-003 track=ALL → INVALID_TRACK_SELECTION (저장/업로드 미수행)")
        void create_track_all_rejected() {
            ArchiveCreateRequest request = validProjectRequest();
            ReflectionTestUtils.setField(request, "track", Track.ALL);

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.PROJECT, request, validImage()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);

            verify(s3Service, never()).uploadImage(any(), any(), any());
            verify(archiveRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-004 links 파싱 불가 → INVALID_INPUT_VALUE")
        void create_links_unparseable() {
            ArchiveCreateRequest request = validProjectRequest();
            ReflectionTestUtils.setField(request, "links", "{bad");

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.PROJECT, request, validImage()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("TC-004 links 객체 아님(배열) → INVALID_INPUT_VALUE")
        void create_links_not_object() {
            ArchiveCreateRequest request = validProjectRequest();
            ReflectionTestUtils.setField(request, "links", "[\"https://a.com\"]");

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.PROJECT, request, validImage()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("TC-004 links value URL 형식 아님 → INVALID_URL_FORMAT")
        void create_links_invalid_url() {
            ArchiveCreateRequest request = validProjectRequest();
            ReflectionTestUtils.setField(request, "links", "{\"slideshare\":\"not-a-url\"}");

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.PROJECT, request, validImage()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_URL_FORMAT);
        }

        @Test
        @DisplayName("TC-005 image 빈 파일 → INVALID_FILE_TYPE")
        void create_image_empty() {
            MockMultipartFile empty = new MockMultipartFile("image", "photo.png", "image/png", new byte[0]);

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.PROJECT, validProjectRequest(), empty))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_FILE_TYPE);
        }

        @Test
        @DisplayName("TC-005 허용 외 확장자(gif) → INVALID_FILE_TYPE")
        void create_image_bad_extension() {
            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.PROJECT, validProjectRequest(), image("photo.gif")))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_FILE_TYPE);
        }

        @Test
        @DisplayName("TC-005 파일명 없음(originalFilename=null) → INVALID_FILE_TYPE")
        void create_image_null_filename() {
            MultipartFile nullName = mock(MultipartFile.class);
            when(nullName.isEmpty()).thenReturn(false);
            when(nullName.getSize()).thenReturn(3L);
            when(nullName.getOriginalFilename()).thenReturn(null);

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.PROJECT, validProjectRequest(), nullName))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_FILE_TYPE);
        }

        @Test
        @DisplayName("TC-005 이미지 5MB 초과 → FILE_SIZE_EXCEEDED")
        void create_image_too_large() {
            assertThatThrownBy(() -> archiveAdminService.createArchive(
                    Category.PROJECT, validProjectRequest(), sizedImage(5L * 1024 * 1024 + 1)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        @Test
        @DisplayName("TC-005 이미지 정확히 5MB → 통과")
        void create_image_exactly_max() {
            when(s3Service.uploadImage(any(), any(), any())).thenReturn(NEW_IMAGE_URL);

            archiveAdminService.createArchive(Category.PROJECT, validProjectRequest(), sizedImage(5L * 1024 * 1024));

            verify(archiveRepository).save(any());
        }

        @Test
        @DisplayName("TC-006 S3 업로드 실패 → S3_UPLOAD_FAILED 전파, save 미호출")
        void create_s3_upload_failed() {
            when(s3Service.uploadImage(any(), any(), any()))
                    .thenThrow(new CustomException(ErrorCode.S3_UPLOAD_FAILED));

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.PROJECT, validProjectRequest(), validImage()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.S3_UPLOAD_FAILED);

            verify(archiveRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-007 DB save 실패 → 업로드 이미지 삭제 + INTERNAL_SERVER_ERROR")
        void create_db_failure_cleans_up_s3() {
            when(s3Service.uploadImage(any(), any(), any())).thenReturn(NEW_IMAGE_URL);
            when(archiveRepository.save(any())).thenThrow(new RuntimeException("db down"));

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.PROJECT, validProjectRequest(), validImage()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

            verify(s3Service).deleteImage(BUCKET, NEW_IMAGE_URL);
        }

        @Test
        @DisplayName("TC-007 비고: DB 실패 + catch 내부 S3 정리마저 실패 → 로그만, INTERNAL_SERVER_ERROR 전파")
        void create_db_failure_and_cleanup_failure_still_internal_error() {
            when(s3Service.uploadImage(any(), any(), any())).thenReturn(NEW_IMAGE_URL);
            when(archiveRepository.save(any())).thenThrow(new RuntimeException("db down"));
            doThrow(new CustomException(ErrorCode.S3_DELETE_FAILED)).when(s3Service).deleteImage(BUCKET, NEW_IMAGE_URL);

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.PROJECT, validProjectRequest(), validImage()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

            verify(s3Service).deleteImage(BUCKET, NEW_IMAGE_URL);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-006 활동사진 등록
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-006 활동사진 등록")
    class CreateActivity {

        @Test
        @DisplayName("TC-001 유효 요청 + half → 등록 성공, S3 key에 half 폴더 포함")
        void create_success_key_contains_half() {
            ArchiveCreateRequest request = createRequest(26, "가을 MT", "팀보아즈", Track.ENGINEERING,
                    "{}", LocalDate.of(2026, 7, 1), "26-2");
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            when(s3Service.uploadImage(any(), keyCaptor.capture(), any())).thenReturn(NEW_IMAGE_URL);

            archiveAdminService.createArchive(Category.ACTIVITY, request, image("a.jpeg"));

            verify(archiveRepository).save(any());
            assertThat(keyCaptor.getValue()).matches("^activities/26기/26-2/엔지니어링/가을-MT-\\d{12}\\.jpeg$");
        }

        @Test
        @DisplayName("TC-002 half=null → MISSING_HALF (이후 로직 미실행)")
        void create_missing_half() {
            ArchiveCreateRequest request = createRequest(26, "제목", "팀", Track.ANALYSIS,
                    "{}", LocalDate.of(2026, 7, 1), null);

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.ACTIVITY, request, validImage()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.MISSING_HALF);

            verify(s3Service, never()).uploadImage(any(), any(), any());
            verify(archiveRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-003 track=ALL → 등록 허용 (validateNotAll 미수행)")
        void create_track_all_allowed() {
            ArchiveCreateRequest request = createRequest(26, "전체 세미나", "팀", Track.ALL,
                    "{}", LocalDate.of(2026, 7, 1), "26-1");
            ArgumentCaptor<Archive> saved = ArgumentCaptor.forClass(Archive.class);
            when(s3Service.uploadImage(any(), any(), any())).thenReturn(NEW_IMAGE_URL);

            archiveAdminService.createArchive(Category.ACTIVITY, request, validImage());

            verify(archiveRepository).save(saved.capture());
            assertThat(saved.getValue().getTrack()).isEqualTo(Track.ALL);
        }

        // ── ARC-005 공통 규칙을 category=ACTIVITY 로 동일 적용 (명세 "동일하게 적용한다") ──

        private ArchiveCreateRequest activityRequest(String links) {
            return createRequest(26, "제목", "팀", Track.ANALYSIS, links, LocalDate.of(2026, 7, 1), "26-1");
        }

        @Test
        @DisplayName("TC-004(공통) links — 파싱 불가/객체 아님 → INVALID_INPUT_VALUE, URL 형식 → INVALID_URL_FORMAT")
        void create_links_validation() {
            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.ACTIVITY, activityRequest("{bad"), validImage()))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.ACTIVITY, activityRequest("[\"https://a.com\"]"), validImage()))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.ACTIVITY, activityRequest("{\"k\":\"not-a-url\"}"), validImage()))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_URL_FORMAT);
        }

        @Test
        @DisplayName("TC-005(공통) image — 빈 파일/확장자 gif → INVALID_FILE_TYPE, 5MB 초과 → FILE_SIZE_EXCEEDED")
        void create_image_validation() {
            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.ACTIVITY, activityRequest("{}"),
                    new MockMultipartFile("image", "p.png", "image/png", new byte[0])))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_FILE_TYPE);
            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.ACTIVITY, activityRequest("{}"), image("p.gif")))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_FILE_TYPE);
            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.ACTIVITY, activityRequest("{}"), sizedImage(5L * 1024 * 1024 + 1)))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        @Test
        @DisplayName("TC-006(공통) S3 업로드 실패 → S3_UPLOAD_FAILED 전파, save 미호출")
        void create_s3_upload_failed() {
            when(s3Service.uploadImage(any(), any(), any())).thenThrow(new CustomException(ErrorCode.S3_UPLOAD_FAILED));

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.ACTIVITY, activityRequest("{}"), validImage()))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.S3_UPLOAD_FAILED);
            verify(archiveRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-007(공통) DB save 실패 → 업로드 이미지 삭제 + INTERNAL_SERVER_ERROR")
        void create_db_failure_cleans_up_s3() {
            when(s3Service.uploadImage(any(), any(), any())).thenReturn(NEW_IMAGE_URL);
            when(archiveRepository.save(any())).thenThrow(new RuntimeException("db"));

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.ACTIVITY, activityRequest("{}"), validImage()))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
            verify(s3Service).deleteImage(BUCKET, NEW_IMAGE_URL);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-007 기술블로그 등록
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-007 기술블로그 등록")
    class CreateBlog {

        @Test
        @DisplayName("TC-001 유효 요청 → save 호출 + S3 key 규칙(half 폴더 없음)")
        void create_success_key_without_half() {
            ArchiveCreateRequest request = createRequest(8, "Transformer 구현기", "개인", Track.ENGINEERING,
                    "{\"velog\":\"https://velog.io/x\"}", LocalDate.of(2024, 7, 1), null);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            when(s3Service.uploadImage(any(), keyCaptor.capture(), any())).thenReturn(NEW_IMAGE_URL);

            archiveAdminService.createArchive(Category.BLOG, request, image("cover.webp"));

            verify(archiveRepository).save(any());
            assertThat(keyCaptor.getValue()).matches("^blogs/8기/엔지니어링/Transformer-구현기-\\d{12}\\.webp$");
        }

        @Test
        @DisplayName("TC-002 track=ALL → INVALID_TRACK_SELECTION")
        void create_track_all_rejected() {
            ArchiveCreateRequest request = createRequest(8, "제목", "개인", Track.ALL,
                    "{}", LocalDate.of(2024, 7, 1), null);

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.BLOG, request, validImage()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);

            verify(archiveRepository, never()).save(any());
        }

        // ── ARC-005 공통 규칙을 category=BLOG 로 동일 적용 (명세 "동일하게 적용한다") ──

        private ArchiveCreateRequest blogRequest(String teamName, String links) {
            return createRequest(8, "제목", teamName, Track.ENGINEERING, links, LocalDate.of(2024, 7, 1), null);
        }

        @Test
        @DisplayName("TC-002(공통) teamName null / links {} 도 등록 성공")
        void create_optional_values() {
            when(s3Service.uploadImage(any(), any(), any())).thenReturn(NEW_IMAGE_URL);

            archiveAdminService.createArchive(Category.BLOG, blogRequest(null, "{}"), validImage());

            ArgumentCaptor<Archive> saved = ArgumentCaptor.forClass(Archive.class);
            verify(archiveRepository).save(saved.capture());
            assertThat(saved.getValue().getTeamName()).isNull();
        }

        @Test
        @DisplayName("TC-004(공통) links — 파싱 불가/객체 아님 → INVALID_INPUT_VALUE, URL 형식 → INVALID_URL_FORMAT")
        void create_links_validation() {
            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.BLOG, blogRequest("개인", "{bad"), validImage()))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.BLOG, blogRequest("개인", "[\"https://a.com\"]"), validImage()))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.BLOG, blogRequest("개인", "{\"k\":\"not-a-url\"}"), validImage()))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_URL_FORMAT);
        }

        @Test
        @DisplayName("TC-005(공통) image — 빈 파일/확장자 gif → INVALID_FILE_TYPE, 5MB 초과 → FILE_SIZE_EXCEEDED")
        void create_image_validation() {
            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.BLOG, blogRequest("개인", "{}"),
                    new MockMultipartFile("image", "p.png", "image/png", new byte[0])))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_FILE_TYPE);
            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.BLOG, blogRequest("개인", "{}"), image("p.gif")))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_FILE_TYPE);
            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.BLOG, blogRequest("개인", "{}"), sizedImage(5L * 1024 * 1024 + 1)))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        @Test
        @DisplayName("TC-006(공통) S3 업로드 실패 → S3_UPLOAD_FAILED 전파, save 미호출")
        void create_s3_upload_failed() {
            when(s3Service.uploadImage(any(), any(), any())).thenThrow(new CustomException(ErrorCode.S3_UPLOAD_FAILED));

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.BLOG, blogRequest("개인", "{}"), validImage()))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.S3_UPLOAD_FAILED);
            verify(archiveRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-007(공통) DB save 실패 → 업로드 이미지 삭제 + INTERNAL_SERVER_ERROR")
        void create_db_failure_cleans_up_s3() {
            when(s3Service.uploadImage(any(), any(), any())).thenReturn(NEW_IMAGE_URL);
            when(archiveRepository.save(any())).thenThrow(new RuntimeException("db"));

            assertThatThrownBy(() -> archiveAdminService.createArchive(Category.BLOG, blogRequest("개인", "{}"), validImage()))
                    .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
            verify(s3Service).deleteImage(BUCKET, NEW_IMAGE_URL);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-008 프로젝트 수정
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-008 프로젝트 수정")
    class UpdateProject {

        @Test
        @DisplayName("TC-001 없는 id → ARCHIVE_NOT_FOUND")
        void update_not_found() {
            when(archiveRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> archiveAdminService.updateArchive(Category.PROJECT, 999L, updateRequest(), null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ARCHIVE_NOT_FOUND);
        }

        @Test
        @DisplayName("TC-001 category 불일치(id가 BLOG) → UNSUPPORTED_ARCHIVE_CATEGORY")
        void update_category_mismatch() {
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive(Category.BLOG, "s3://old")));

            assertThatThrownBy(() -> archiveAdminService.updateArchive(Category.PROJECT, 1L, updateRequest(), null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_ARCHIVE_CATEGORY);
        }

        @Test
        @DisplayName("TC-002 request=null && image 없음 → INVALID_INPUT_VALUE")
        void update_empty_request() {
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive(Category.PROJECT, "s3://old")));

            assertThatThrownBy(() -> archiveAdminService.updateArchive(Category.PROJECT, 1L, null, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("TC-003 title만 전달 → title만 변경, 나머지 필드 유지")
        void update_partial_title_only() {
            Archive a = archive(Category.PROJECT, "s3://old");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            ArchiveUpdateRequest request = updateRequest();
            ReflectionTestUtils.setField(request, "title", "new title");

            archiveAdminService.updateArchive(Category.PROJECT, 1L, request, null);

            assertThat(a.getTitle()).isEqualTo("new title");
            assertThat(a.getTerm()).isEqualTo(8);
            assertThat(a.getTrack()).isEqualTo(Track.ANALYSIS);
            assertThat(a.getTeamName()).isEqualTo("팀A");
            assertThat(a.getContentDate()).isEqualTo(LocalDate.of(2024, 1, 1));
            assertThat(a.getImageUrl()).isEqualTo("s3://old");
            verify(archiveRepository).flush();
            verify(s3Service, never()).uploadImage(any(), any(), any());
        }

        @Test
        @DisplayName("TC-004 teamName/contentDate 명시적 null → 각각 null로 비움")
        void update_explicit_null_clears() {
            Archive a = archive(Category.PROJECT, "s3://old");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            ArchiveUpdateRequest request = updateRequest();
            ReflectionTestUtils.setField(request, "teamName", JsonNullable.of(null));
            ReflectionTestUtils.setField(request, "contentDate", JsonNullable.of((LocalDate) null));

            archiveAdminService.updateArchive(Category.PROJECT, 1L, request, null);

            assertThat(a.getTeamName()).isNull();
            assertThat(a.getContentDate()).isNull();
        }

        @Test
        @DisplayName("TC-004 title=\"\" → 기존 제목이 \"\"로 덮어써짐 (현행 동작)")
        void update_blank_title_overwrites() {
            Archive a = archive(Category.PROJECT, "s3://old");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            ArchiveUpdateRequest request = updateRequest();
            ReflectionTestUtils.setField(request, "title", "");

            archiveAdminService.updateArchive(Category.PROJECT, 1L, request, null);

            assertThat(a.getTitle()).isEmpty();
        }

        @Test
        @DisplayName("TC-005 track=ALL → INVALID_TRACK_SELECTION")
        void update_track_all_rejected() {
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive(Category.PROJECT, "s3://old")));
            ArchiveUpdateRequest request = updateRequest();
            ReflectionTestUtils.setField(request, "track", Track.ALL);

            assertThatThrownBy(() -> archiveAdminService.updateArchive(Category.PROJECT, 1L, request, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);

            verify(archiveRepository, never()).flush();
        }

        @Test
        @DisplayName("TC-006 contentDate 미래 → FUTURE_DATE_NOT_ALLOWED")
        void update_future_date_rejected() {
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive(Category.PROJECT, "s3://old")));
            ArchiveUpdateRequest request = updateRequest();
            ReflectionTestUtils.setField(request, "contentDate", JsonNullable.of(LocalDate.now().plusDays(1)));

            assertThatThrownBy(() -> archiveAdminService.updateArchive(Category.PROJECT, 1L, request, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FUTURE_DATE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("TC-006 contentDate 오늘 → 통과")
        void update_today_date_allowed() {
            Archive a = archive(Category.PROJECT, "s3://old");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            ArchiveUpdateRequest request = updateRequest();
            ReflectionTestUtils.setField(request, "contentDate", JsonNullable.of(LocalDate.now()));

            archiveAdminService.updateArchive(Category.PROJECT, 1L, request, null);

            assertThat(a.getContentDate()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("TC-007 links 빈 문자열 → INVALID_INPUT_VALUE (빈 문자열로 비울 수 없음)")
        void update_blank_links_rejected() {
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive(Category.PROJECT, "s3://old")));
            ArchiveUpdateRequest request = updateRequest();
            ReflectionTestUtils.setField(request, "links", "");

            assertThatThrownBy(() -> archiveAdminService.updateArchive(Category.PROJECT, 1L, request, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("TC-007 links 파싱 불가 → INVALID_INPUT_VALUE / URL 형식 오류 → INVALID_URL_FORMAT")
        void update_links_validation() {
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive(Category.PROJECT, "s3://old")));

            ArchiveUpdateRequest unparseable = updateRequest();
            ReflectionTestUtils.setField(unparseable, "links", "{bad");
            assertThatThrownBy(() -> archiveAdminService.updateArchive(Category.PROJECT, 1L, unparseable, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

            ArchiveUpdateRequest badUrl = updateRequest();
            ReflectionTestUtils.setField(badUrl, "links", "{\"a\":\"not-a-url\"}");
            assertThatThrownBy(() -> archiveAdminService.updateArchive(Category.PROJECT, 1L, badUrl, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_URL_FORMAT);
        }

        @Test
        @DisplayName("TC-008 새 이미지 업로드 → imageUrl 갱신 + 커밋 후 기존 이미지 삭제 예약")
        void update_new_image_schedules_old_delete() {
            Archive a = archive(Category.PROJECT, "https://bucket/old.png");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            when(s3Service.uploadImage(any(), any(), any())).thenReturn("https://bucket/new.png");

            archiveAdminService.updateArchive(Category.PROJECT, 1L, updateRequest(), validImage());

            assertThat(a.getImageUrl()).isEqualTo("https://bucket/new.png");
            verify(s3Service, never()).deleteImage(any(), any()); // 메서드 실행 중에는 미호출

            triggerAfterCommit();
            verify(s3Service).deleteImage(BUCKET, "https://bucket/old.png");
        }

        @Test
        @DisplayName("TC-008 새 이미지 업로드했으나 기존 imageUrl이 null → 커밋 후 삭제 콜백 미등록")
        void update_new_image_old_null_no_callback() {
            Archive a = archive(Category.PROJECT, null);
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            when(s3Service.uploadImage(any(), any(), any())).thenReturn("https://bucket/new.png");

            archiveAdminService.updateArchive(Category.PROJECT, 1L, updateRequest(), validImage());

            // rollbackCleanup 1건만 등록, deleteAfterCommit 미등록
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            triggerAfterCommit();
            verify(s3Service, never()).deleteImage(any(), any());
        }

        @Test
        @DisplayName("TC-009 이미지 업로드 후 flush 실패 → 새 이미지 삭제 + INTERNAL_SERVER_ERROR")
        void update_flush_failure_cleans_new_image() {
            Archive a = archive(Category.PROJECT, "https://bucket/old.png");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            when(s3Service.uploadImage(any(), any(), any())).thenReturn("https://bucket/new.png");
            doThrow(new RuntimeException("db")).when(archiveRepository).flush();

            assertThatThrownBy(() -> archiveAdminService.updateArchive(Category.PROJECT, 1L, updateRequest(), validImage()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

            verify(s3Service).deleteImage(BUCKET, "https://bucket/new.png");
            verify(s3Service, never()).deleteImage(BUCKET, "https://bucket/old.png");
        }

        @Test
        @DisplayName("TC-009 request-only 수정(이미지 없음) flush 실패 → S3 작업 없이 INTERNAL_SERVER_ERROR")
        void update_request_only_flush_failure_no_s3() {
            Archive a = archive(Category.PROJECT, "s3://old");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            ArchiveUpdateRequest request = updateRequest();
            ReflectionTestUtils.setField(request, "title", "new");
            doThrow(new RuntimeException("db")).when(archiveRepository).flush();

            assertThatThrownBy(() -> archiveAdminService.updateArchive(Category.PROJECT, 1L, request, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

            verify(s3Service, never()).deleteImage(any(), any());
        }

        @Test
        @DisplayName("TC-009 이미지 업로드가 S3_UPLOAD_FAILED → 정리 없이 전파, flush 미호출")
        void update_s3_upload_failed_propagates() {
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive(Category.PROJECT, "s3://old")));
            when(s3Service.uploadImage(any(), any(), any())).thenThrow(new CustomException(ErrorCode.S3_UPLOAD_FAILED));

            assertThatThrownBy(() -> archiveAdminService.updateArchive(Category.PROJECT, 1L, updateRequest(), validImage()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.S3_UPLOAD_FAILED);

            verify(archiveRepository, never()).flush();
            verify(s3Service, never()).deleteImage(any(), any());
        }

        @Test
        @DisplayName("TC-010 image만 전달(request=null) → 이미지만 교체, teamName/contentDate 유지")
        void update_image_only() {
            Archive a = archive(Category.PROJECT, "https://bucket/old.png");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            when(s3Service.uploadImage(any(), any(), any())).thenReturn("https://bucket/new.png");

            archiveAdminService.updateArchive(Category.PROJECT, 1L, null, validImage());

            assertThat(a.getImageUrl()).isEqualTo("https://bucket/new.png");
            assertThat(a.getTeamName()).isEqualTo("팀A");
            assertThat(a.getContentDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-009 활동사진 수정
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-009 활동사진 수정")
    class UpdateActivity {

        private Archive activityArchive(String imageUrl) {
            return Archive.builder()
                    .term(26).category(Category.ACTIVITY).title("여름 세미나").teamName("팀A").track(Track.ANALYSIS)
                    .imageUrl(imageUrl).links("{}").contentDate(LocalDate.of(2026, 1, 1))
                    .build();
        }

        @Test
        @DisplayName("TC-001 track=ALL로 수정 → 허용")
        void update_track_all_allowed() {
            Archive a = activityArchive("https://bucket/activities/26기/26-1/분석/old.png");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            ArchiveUpdateRequest request = updateRequest();
            ReflectionTestUtils.setField(request, "track", Track.ALL);

            archiveAdminService.updateArchive(Category.ACTIVITY, 1L, request, null);

            assertThat(a.getTrack()).isEqualTo(Track.ALL);
            verify(archiveRepository).flush();
        }

        @Test
        @DisplayName("TC-002 image만 전달 → 기존 imageUrl의 half 세그먼트를 유지한 새 key 생성")
        void update_image_only_reuses_half_from_url() {
            Archive a = activityArchive("https://bucket/activities/26기/26-1/분석/여름-세미나-250701120000.png");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            when(s3Service.uploadImage(any(), keyCaptor.capture(), any())).thenReturn("https://bucket/new.png");

            archiveAdminService.updateArchive(Category.ACTIVITY, 1L, null, validImage());

            assertThat(keyCaptor.getValue()).matches("^activities/26기/26-1/분석/여름-세미나-\\d{12}\\.png$");
        }

        @Test
        @DisplayName("TC-002 request.half 전달 시 → 파싱 대신 request.half로 key 생성")
        void update_uses_request_half() {
            Archive a = activityArchive("https://bucket/activities/26기/26-1/분석/old.png");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            ArchiveUpdateRequest request = updateRequest();
            ReflectionTestUtils.setField(request, "half", "26-2");
            ReflectionTestUtils.setField(request, "title", "가을 세미나");
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            when(s3Service.uploadImage(any(), keyCaptor.capture(), any())).thenReturn("https://bucket/new.png");

            archiveAdminService.updateArchive(Category.ACTIVITY, 1L, request, validImage());

            assertThat(keyCaptor.getValue()).matches("^activities/26기/26-2/분석/가을-세미나-\\d{12}\\.png$");
        }

        @Test
        @DisplayName("TC-003 기존 imageUrl에 half 세그먼트 없음 → key에 \"null\" 포함 (현행 동작)")
        void update_image_only_no_half_segment() {
            Archive a = activityArchive("https://bucket/activities/26기/분석/old.png");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            when(s3Service.uploadImage(any(), keyCaptor.capture(), any())).thenReturn("https://bucket/new.png");

            archiveAdminService.updateArchive(Category.ACTIVITY, 1L, null, validImage());

            assertThat(keyCaptor.getValue()).startsWith("activities/26기/null/분석/");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-010 기술블로그 수정
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-010 기술블로그 수정")
    class UpdateBlog {

        @Test
        @DisplayName("TC-001 track=ALL (BLOG) → INVALID_TRACK_SELECTION")
        void update_track_all_rejected() {
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive(Category.BLOG, "s3://old")));
            ArchiveUpdateRequest request = updateRequest();
            ReflectionTestUtils.setField(request, "track", Track.ALL);

            assertThatThrownBy(() -> archiveAdminService.updateArchive(Category.BLOG, 1L, request, null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRACK_SELECTION);

            verify(archiveRepository, never()).flush();
        }

        @Test
        @DisplayName("TC-002 새 이미지 업로드 → S3 key에 half 폴더 없음 + 커밋 후 기존 이미지 삭제")
        void update_new_image_key_without_half() {
            Archive a = archive(Category.BLOG, "https://bucket/old.png");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            when(s3Service.uploadImage(any(), keyCaptor.capture(), any())).thenReturn("https://bucket/new.png");

            archiveAdminService.updateArchive(Category.BLOG, 1L, updateRequest(), validImage());

            assertThat(keyCaptor.getValue()).matches("^blogs/\\d+기/(분석|시각화|엔지니어링|전체)/.+-\\d{12}\\.\\w+$");
            triggerAfterCommit();
            verify(s3Service).deleteImage(BUCKET, "https://bucket/old.png");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-011 프로젝트 삭제
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-011 프로젝트 삭제")
    class DeleteProject {

        @Test
        @DisplayName("TC-001 존재하는 PROJECT 삭제 → delete 호출 + 커밋 후 S3 이미지 삭제")
        void delete_success_schedules_s3_delete() {
            Archive a = archive(Category.PROJECT, "https://bucket/p.png");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));

            archiveAdminService.deleteArchive(Category.PROJECT, 1L);

            verify(archiveRepository).delete(a);
            verify(s3Service, never()).deleteImage(any(), any());
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            triggerAfterCommit();
            verify(s3Service).deleteImage(BUCKET, "https://bucket/p.png");
        }

        @Test
        @DisplayName("TC-001 imageUrl=null → S3 삭제 콜백 미등록")
        void delete_null_image_no_callback() {
            Archive a = archive(Category.PROJECT, null);
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));

            archiveAdminService.deleteArchive(Category.PROJECT, 1L);

            verify(archiveRepository).delete(a);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        }

        @Test
        @DisplayName("TC-002 없는 id → ARCHIVE_NOT_FOUND (delete 미호출)")
        void delete_not_found() {
            when(archiveRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> archiveAdminService.deleteArchive(Category.PROJECT, 999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ARCHIVE_NOT_FOUND);

            verify(archiveRepository, never()).delete(any());
        }

        @Test
        @DisplayName("TC-002 category 불일치 → UNSUPPORTED_ARCHIVE_CATEGORY (delete 미호출)")
        void delete_category_mismatch() {
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive(Category.BLOG, "s3://old")));

            assertThatThrownBy(() -> archiveAdminService.deleteArchive(Category.PROJECT, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_ARCHIVE_CATEGORY);

            verify(archiveRepository, never()).delete(any());
        }

        @Test
        @DisplayName("TC-003 delete 중 예외 → 그대로 전파(도메인 코드 아님), S3 삭제 콜백 미등록")
        void delete_repository_throws_propagates() {
            Archive a = archive(Category.PROJECT, "https://bucket/p.png");
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(a));
            doThrow(new RuntimeException("db")).when(archiveRepository).delete(a);

            assertThatThrownBy(() -> archiveAdminService.deleteArchive(Category.PROJECT, 1L))
                    .isInstanceOf(RuntimeException.class);

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARC-012 / ARC-013 활동사진·기술블로그 삭제 (카테고리 고정 검증)
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ARC-012 활동사진 삭제 / ARC-013 기술블로그 삭제")
    class DeleteActivityAndBlog {

        @Test
        @DisplayName("ARC-012 TC-001 id의 category가 PROJECT → UNSUPPORTED_ARCHIVE_CATEGORY")
        void delete_activity_category_mismatch() {
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive(Category.PROJECT, "s3://old")));

            assertThatThrownBy(() -> archiveAdminService.deleteArchive(Category.ACTIVITY, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_ARCHIVE_CATEGORY);

            verify(archiveRepository, never()).delete(any());
        }

        @Test
        @DisplayName("ARC-013 TC-001 id의 category가 ACTIVITY → UNSUPPORTED_ARCHIVE_CATEGORY")
        void delete_blog_category_mismatch() {
            when(archiveRepository.findById(1L)).thenReturn(Optional.of(archive(Category.ACTIVITY, "s3://old")));

            assertThatThrownBy(() -> archiveAdminService.deleteArchive(Category.BLOG, 1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_ARCHIVE_CATEGORY);

            verify(archiveRepository, never()).delete(any());
        }
    }
}
