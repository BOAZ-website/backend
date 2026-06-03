package com.boaz.backend.domain.archive.service;

import com.boaz.backend.domain.archive.dto.response.ArchivePageResponse;
import com.boaz.backend.domain.archive.dto.response.ArchiveTermsResponse;
import com.boaz.backend.domain.archive.entity.Archive;
import com.boaz.backend.domain.archive.entity.Archive.Category;
import com.boaz.backend.domain.archive.repository.ArchiveRepository;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.exception.CustomException;
import com.boaz.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {

    @InjectMocks ArchiveService archiveService;
    @Mock ArchiveRepository archiveRepository;

    private Archive buildArchive(int term, Category category, String title, Track track, LocalDate contentDate) {
        return Archive.builder()
                .term(term).category(category).title(title)
                .track(track).imageUrl("http://img.example.com/test.jpg")
                .links("{}").contentDate(contentDate)
                .build();
    }

    private Page<Archive> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 6), 0L);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ARC-001 프로젝트 아카이빙 조회
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("ARC-001 프로젝트 아카이빙 조회")
    class ProjectSearch {

        @Nested
        @DisplayName("[그룹 A] normalizeKeyword")
        class NormalizeKeyword {

            @Test
            @DisplayName("TC-001 keyword=null → Repository에 null 전달")
            void keyword_null() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.PROJECT, Track.ALL, null, null, 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.PROJECT), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }

            @Test
            @DisplayName("TC-002 keyword=\"\" → null 정규화 후 Repository에 null 전달")
            void keyword_empty() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.PROJECT, Track.ALL, null, "", 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.PROJECT), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }

            @Test
            @DisplayName("TC-003 keyword 공백만 → null 정규화 후 Repository에 null 전달")
            void keyword_blank() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.PROJECT, Track.ALL, null, "   ", 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.PROJECT), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }

            @Test
            @DisplayName("TC-004 keyword 앞뒤 공백 → trim 후 Repository에 전달")
            void keyword_trim() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.PROJECT, Track.ALL, null, "  딥러닝  ", 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.PROJECT), isNull(), isNull(), eq("딥러닝"), eq(PageRequest.of(0, 6)));
            }
        }

        @Nested
        @DisplayName("[그룹 B] track 변환")
        class TrackConversion {

            @Test
            @DisplayName("TC-005 track=ALL → null 변환 후 Repository에 전달")
            void track_all_to_null() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.PROJECT, Track.ALL, null, null, 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.PROJECT), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }

            @Test
            @DisplayName("TC-006 track=ENGINEERING → null 변환 없이 그대로 전달")
            void track_specific() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.PROJECT, Track.ENGINEERING, null, null, 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.PROJECT), eq(Track.ENGINEERING), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }
        }

        @Nested
        @DisplayName("[그룹 C] 페이지네이션 변환 + 응답 DTO 변환")
        class PaginationConversion {

            @Test
            @DisplayName("TC-007 page=2, size=3 → 0기반 변환 + currentPage/totalSize 검증")
            void pagination_conversion() {
                Archive archive = buildArchive(26, Category.PROJECT, "실시간 추천 시스템", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                Page<Archive> mockPage = new PageImpl<>(List.of(archive), PageRequest.of(1, 3), 7L);
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(mockPage);

                ArchivePageResponse response = archiveService.searchArchives(Category.PROJECT, Track.ALL, null, null, 2, 3);

                verify(archiveRepository).searchArchives(
                        eq(Category.PROJECT), isNull(), isNull(), isNull(), eq(PageRequest.of(1, 3)));
                assertThat(response.getCurrentPage()).isEqualTo(2);
                assertThat(response.getTotalSize()).isEqualTo(7L);
                assertThat(response.isHasPrevious()).isTrue();
                assertThat(response.isHasNext()).isTrue();
            }
        }

        @Nested
        @DisplayName("[그룹 D] 예외")
        class ValidationExceptions {

            @Test
            @DisplayName("TC-008 page=0 → INVALID_PAGINATION")
            void page_zero() {
                assertThatThrownBy(() -> archiveService.searchArchives(Category.PROJECT, Track.ALL, null, null, 0, 6))
                        .isInstanceOf(CustomException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAGINATION);
            }

            @Test
            @DisplayName("TC-009 size=0 → INVALID_PAGINATION")
            void size_zero() {
                assertThatThrownBy(() -> archiveService.searchArchives(Category.PROJECT, Track.ALL, null, null, 1, 0))
                        .isInstanceOf(CustomException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAGINATION);
            }

            @Test
            @DisplayName("TC-010 size=101 → INVALID_PAGINATION")
            void size_over_max() {
                assertThatThrownBy(() -> archiveService.searchArchives(Category.PROJECT, Track.ALL, null, null, 1, 101))
                        .isInstanceOf(CustomException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAGINATION);
            }

            @Test
            @DisplayName("TC-011 term=-1 → INVALID_PARAMETER_TYPE")
            void term_negative() {
                assertThatThrownBy(() -> archiveService.searchArchives(Category.PROJECT, Track.ALL, -1, null, 1, 6))
                        .isInstanceOf(CustomException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PARAMETER_TYPE);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ARC-002 활동사진 아카이빙 조회
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("ARC-002 활동사진 아카이빙 조회")
    class ActivitySearch {

        @Nested
        @DisplayName("[그룹 A] normalizeKeyword")
        class NormalizeKeyword {

            @Test
            @DisplayName("TC-001 keyword=null → Repository에 null 전달")
            void keyword_null() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.ACTIVITY, null, null, null, 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.ACTIVITY), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }

            @Test
            @DisplayName("TC-002 keyword=\"\" → null 정규화 후 Repository에 null 전달")
            void keyword_empty() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.ACTIVITY, null, null, "", 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.ACTIVITY), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }

            @Test
            @DisplayName("TC-003 keyword 공백만 → null 정규화 후 Repository에 null 전달")
            void keyword_blank() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.ACTIVITY, null, null, "   ", 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.ACTIVITY), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }

            @Test
            @DisplayName("TC-004 keyword 앞뒤 공백 → trim 후 Repository에 전달")
            void keyword_trim() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.ACTIVITY, null, null, "  활동사진  ", 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.ACTIVITY), isNull(), isNull(), eq("활동사진"), eq(PageRequest.of(0, 6)));
            }
        }

        @Nested
        @DisplayName("[그룹 B] track 고정 null 확인")
        class TrackFixedNull {

            @Test
            @DisplayName("TC-005 컨트롤러가 null 하드코딩 → null 그대로 Repository에 전달")
            void track_fixed_null() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.ACTIVITY, null, null, null, 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.ACTIVITY), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }
        }

        @Nested
        @DisplayName("[그룹 C] 페이지네이션 변환 + 응답 DTO 변환")
        class PaginationConversion {

            @Test
            @DisplayName("TC-006 page=2, size=3 → 0기반 변환 + currentPage/totalSize 검증")
            void pagination_conversion() {
                Archive archive = buildArchive(26, Category.ACTIVITY, "26기 워크샵 활동", Track.ALL, LocalDate.of(2026, 1, 1));
                Page<Archive> mockPage = new PageImpl<>(List.of(archive), PageRequest.of(1, 3), 7L);
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(mockPage);

                ArchivePageResponse response = archiveService.searchArchives(Category.ACTIVITY, null, null, null, 2, 3);

                verify(archiveRepository).searchArchives(
                        eq(Category.ACTIVITY), isNull(), isNull(), isNull(), eq(PageRequest.of(1, 3)));
                assertThat(response.getCurrentPage()).isEqualTo(2);
                assertThat(response.getTotalSize()).isEqualTo(7L);
                assertThat(response.isHasPrevious()).isTrue();
                assertThat(response.isHasNext()).isTrue();
            }
        }

        @Nested
        @DisplayName("[그룹 D] 예외")
        class ValidationExceptions {

            @Test
            @DisplayName("TC-007 page=0 → INVALID_PAGINATION")
            void page_zero() {
                assertThatThrownBy(() -> archiveService.searchArchives(Category.ACTIVITY, null, null, null, 0, 6))
                        .isInstanceOf(CustomException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAGINATION);
            }

            @Test
            @DisplayName("TC-008 size=0 → INVALID_PAGINATION")
            void size_zero() {
                assertThatThrownBy(() -> archiveService.searchArchives(Category.ACTIVITY, null, null, null, 1, 0))
                        .isInstanceOf(CustomException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAGINATION);
            }

            @Test
            @DisplayName("TC-009 size=101 → INVALID_PAGINATION")
            void size_over_max() {
                assertThatThrownBy(() -> archiveService.searchArchives(Category.ACTIVITY, null, null, null, 1, 101))
                        .isInstanceOf(CustomException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAGINATION);
            }

            @Test
            @DisplayName("TC-010 term=-1 → INVALID_PARAMETER_TYPE")
            void term_negative() {
                assertThatThrownBy(() -> archiveService.searchArchives(Category.ACTIVITY, null, -1, null, 1, 6))
                        .isInstanceOf(CustomException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PARAMETER_TYPE);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ARC-003 기술블로그 아카이빙 조회
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("ARC-003 기술블로그 아카이빙 조회")
    class BlogSearch {

        @Nested
        @DisplayName("[그룹 A] normalizeKeyword")
        class NormalizeKeyword {

            @Test
            @DisplayName("TC-001 keyword=null → Repository에 null 전달")
            void keyword_null() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.BLOG, Track.ALL, null, null, 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.BLOG), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }

            @Test
            @DisplayName("TC-002 keyword=\"\" → null 정규화 후 Repository에 null 전달")
            void keyword_empty() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.BLOG, Track.ALL, null, "", 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.BLOG), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }

            @Test
            @DisplayName("TC-003 keyword 공백만 → null 정규화 후 Repository에 null 전달")
            void keyword_blank() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.BLOG, Track.ALL, null, "   ", 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.BLOG), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }

            @Test
            @DisplayName("TC-004 keyword 앞뒤 공백 → trim 후 Repository에 전달")
            void keyword_trim() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.BLOG, Track.ALL, null, "   딥러닝  ", 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.BLOG), isNull(), isNull(), eq("딥러닝"), eq(PageRequest.of(0, 6)));
            }
        }

        @Nested
        @DisplayName("[그룹 B] track 변환")
        class TrackConversion {

            @Test
            @DisplayName("TC-005 track=ALL → null 변환 후 Repository에 전달")
            void track_all_to_null() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.BLOG, Track.ALL, null, null, 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.BLOG), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }

            @Test
            @DisplayName("TC-006 track=ENGINEERING → null 변환 없이 그대로 전달")
            void track_specific() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.BLOG, Track.ENGINEERING, null, null, 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.BLOG), eq(Track.ENGINEERING), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }
        }

        @Nested
        @DisplayName("[그룹 C] term null 고정 확인")
        class TermFixedNull {

            @Test
            @DisplayName("TC-007 컨트롤러가 null 하드코딩 → null 그대로 Repository에 전달")
            void term_fixed_null() {
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(emptyPage());

                archiveService.searchArchives(Category.BLOG, Track.ALL, null, null, 1, 6);

                verify(archiveRepository).searchArchives(
                        eq(Category.BLOG), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 6)));
            }
        }

        @Nested
        @DisplayName("[그룹 D] 페이지네이션 변환 + 응답 DTO 변환")
        class PaginationConversion {

            @Test
            @DisplayName("TC-008 page=2, size=3 → 0기반 변환 + currentPage/totalSize 검증")
            void pagination_conversion() {
                Archive archive = buildArchive(26, Category.BLOG, "Transformer 모델 구현기", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                Page<Archive> mockPage = new PageImpl<>(List.of(archive), PageRequest.of(1, 3), 7L);
                when(archiveRepository.searchArchives(any(), any(), any(), any(), any())).thenReturn(mockPage);

                ArchivePageResponse response = archiveService.searchArchives(Category.BLOG, Track.ALL, null, null, 2, 3);

                verify(archiveRepository).searchArchives(
                        eq(Category.BLOG), isNull(), isNull(), isNull(), eq(PageRequest.of(1, 3)));
                assertThat(response.getCurrentPage()).isEqualTo(2);
                assertThat(response.getTotalSize()).isEqualTo(7L);
                assertThat(response.isHasPrevious()).isTrue();
                assertThat(response.isHasNext()).isTrue();
            }
        }

        @Nested
        @DisplayName("[그룹 E] 예외")
        class ValidationExceptions {

            @Test
            @DisplayName("TC-009 page=0 → INVALID_PAGINATION")
            void page_zero() {
                assertThatThrownBy(() -> archiveService.searchArchives(Category.BLOG, Track.ALL, null, null, 0, 6))
                        .isInstanceOf(CustomException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAGINATION);
            }

            @Test
            @DisplayName("TC-010 size=0 → INVALID_PAGINATION")
            void size_zero() {
                assertThatThrownBy(() -> archiveService.searchArchives(Category.BLOG, Track.ALL, null, null, 1, 0))
                        .isInstanceOf(CustomException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAGINATION);
            }

            @Test
            @DisplayName("TC-011 size=101 → INVALID_PAGINATION")
            void size_over_max() {
                assertThatThrownBy(() -> archiveService.searchArchives(Category.BLOG, Track.ALL, null, null, 1, 101))
                        .isInstanceOf(CustomException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PAGINATION);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ARC-004 기수 목록 조회
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("ARC-004 기수 목록 조회")
    class GetTermsTest {

        @Nested
        @DisplayName("[그룹 A] DTO 변환 + Repository 위임")
        class TermLabelConversion {

            @Test
            @DisplayName("TC-001 term 목록 조회 시 value/label 변환 (N기)")
            void term_label_general() {
                when(archiveRepository.findDistinctTermsOrdered()).thenReturn(List.of(26, 25, 24));

                ArchiveTermsResponse response = archiveService.getTerms();

                verify(archiveRepository).findDistinctTermsOrdered();
                assertThat(response.terms()).hasSize(3);
                assertThat(response.terms().get(0).value()).isEqualTo(26);
                assertThat(response.terms().get(0).label()).isEqualTo("26기");
                assertThat(response.terms().get(1).value()).isEqualTo(25);
                assertThat(response.terms().get(2).value()).isEqualTo(24);
            }

            @Test
            @DisplayName("TC-002 term=0 → label \"7기 이전\" 변환")
            void term_zero_label() {
                when(archiveRepository.findDistinctTermsOrdered()).thenReturn(List.of(26, 25, 0));

                ArchiveTermsResponse response = archiveService.getTerms();

                assertThat(response.terms().get(2).value()).isEqualTo(0);
                assertThat(response.terms().get(2).label()).isEqualTo("7기 이전");
            }

            @Test
            @DisplayName("TC-003 term 일반값 → label \"N기\" 변환")
            void term_general_label() {
                when(archiveRepository.findDistinctTermsOrdered()).thenReturn(List.of(26));

                ArchiveTermsResponse response = archiveService.getTerms();

                assertThat(response.terms().get(0).value()).isEqualTo(26);
                assertThat(response.terms().get(0).label()).isEqualTo("26기");
            }

            @Test
            @DisplayName("TC-004 DB에 데이터 없음 → 빈 terms 반환")
            void empty_terms() {
                when(archiveRepository.findDistinctTermsOrdered()).thenReturn(List.of());

                ArchiveTermsResponse response = archiveService.getTerms();

                assertThat(response.terms()).isEmpty();
            }
        }
    }
}
