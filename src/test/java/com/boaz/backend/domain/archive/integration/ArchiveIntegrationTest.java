package com.boaz.backend.domain.archive.integration;

import com.boaz.backend.domain.archive.dto.response.ArchivePageResponse;
import com.boaz.backend.domain.archive.dto.response.ArchiveTermsResponse;
import com.boaz.backend.domain.archive.entity.Archive;
import com.boaz.backend.domain.archive.entity.Archive.Category;
import com.boaz.backend.domain.archive.repository.ArchiveRepository;
import com.boaz.backend.domain.archive.service.ArchiveService;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.support.TestcontainersBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class ArchiveIntegrationTest extends TestcontainersBase {

    @Autowired ArchiveService archiveService;
    @Autowired ArchiveRepository archiveRepository;

    private Archive save(int term, Category category, String title, Track track, LocalDate contentDate) {
        return archiveRepository.save(Archive.builder()
                .term(term).category(category).title(title)
                .track(track).imageUrl("http://img.example.com/test.jpg")
                .links("{}").contentDate(contentDate)
                .build());
    }

    // ══════════════════════════════════════════════
    // ARC-001 프로젝트 조회 end-to-end
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("프로젝트 조회 end-to-end (ARC-001)")
    class ProjectSearch {

        @Nested
        @DisplayName("[그룹 A] keyword 검색 동작")
        class KeywordSearch {

            @Test
            @DisplayName("TC-I-001 keyword 부분 일치 → 제목 중간에 검색어 포함 시 히트")
            void partial_match() {
                save(26, Category.PROJECT, "실시간 추천 시스템 구축", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.PROJECT, "이상 탐지 모델 개발", Track.ANALYSIS, LocalDate.of(2026, 1, 2));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.PROJECT, Track.ALL, null, "추천", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(1);
                assertThat(response.getPosts().get(0).getTitle()).isEqualTo("실시간 추천 시스템 구축");
            }

            @Test
            @DisplayName("TC-I-002 keyword 대소문자 무시 → 영문 대소문자 관계 없이 히트")
            void case_insensitive() {
                save(26, Category.PROJECT, "BOAZ Project", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.PROJECT, "이상 탐지", Track.ANALYSIS, LocalDate.of(2026, 1, 2));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.PROJECT, Track.ALL, null, "boaz", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(1);
                assertThat(response.getPosts().get(0).getTitle()).isEqualTo("BOAZ Project");
            }

            @Test
            @DisplayName("TC-I-003 keyword 공백 무시 → 검색어의 공백 제거 후 매칭")
            void space_insensitive() {
                save(26, Category.PROJECT, "딥러닝프로젝트", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.PROJECT, "이상탐지", Track.ANALYSIS, LocalDate.of(2026, 1, 2));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.PROJECT, Track.ALL, null, "딥 러 닝", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(1);
                assertThat(response.getPosts().get(0).getTitle()).isEqualTo("딥러닝프로젝트");
            }

            @Test
            @DisplayName("TC-I-004 keyword=\"\" → LIKE '%%' → 전체 반환")
            void empty_string_returns_all() {
                save(26, Category.PROJECT, "프로젝트A", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.PROJECT, "프로젝트B", Track.ANALYSIS, LocalDate.of(2026, 1, 2));
                save(26, Category.PROJECT, "프로젝트C", Track.VISUALIZATION, LocalDate.of(2026, 1, 3));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.PROJECT, Track.ALL, null, "", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(3);
            }
        }

        @Nested
        @DisplayName("[그룹 B] 정렬")
        class Sorting {

            @Test
            @DisplayName("TC-I-005 contentDate=null → 맨 뒤 정렬")
            void null_date_last() {
                save(26, Category.PROJECT, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.PROJECT, "B", Track.ENGINEERING, null);
                save(26, Category.PROJECT, "C", Track.ENGINEERING, LocalDate.of(2026, 3, 1));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.PROJECT, Track.ALL, null, null, 1, 10);

                List<String> titles = response.getPosts().stream()
                        .map(p -> p.getTitle()).toList();
                assertThat(titles).containsExactly("C", "A", "B");
            }

            @Test
            @DisplayName("TC-I-006 contentDate 최신순 DESC 정렬")
            void date_desc() {
                save(26, Category.PROJECT, "A", Track.ENGINEERING, LocalDate.of(2025, 5, 1));
                save(26, Category.PROJECT, "B", Track.ENGINEERING, LocalDate.of(2026, 3, 1));
                save(26, Category.PROJECT, "C", Track.ENGINEERING, LocalDate.of(2024, 1, 1));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.PROJECT, Track.ALL, null, null, 1, 10);

                List<String> titles = response.getPosts().stream()
                        .map(p -> p.getTitle()).toList();
                assertThat(titles).containsExactly("B", "A", "C");
            }

            @Test
            @DisplayName("TC-I-007 contentDate 동일할 경우, title ASC tie-break")
            void same_date_title_asc() {
                LocalDate sameDate = LocalDate.of(2026, 1, 1);
                save(26, Category.PROJECT, "나다라", Track.ENGINEERING, sameDate);
                save(26, Category.PROJECT, "가나다", Track.ENGINEERING, sameDate);
                save(26, Category.PROJECT, "다라마", Track.ENGINEERING, sameDate);

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.PROJECT, Track.ALL, null, null, 1, 10);

                List<String> titles = response.getPosts().stream()
                        .map(p -> p.getTitle()).toList();
                assertThat(titles).containsExactly("가나다", "나다라", "다라마");
            }
        }

        @Nested
        @DisplayName("[그룹 C] 필터 후 totalSize")
        class FilterTotalSize {

            @Test
            @DisplayName("TC-I-008 track 필터 적용 시 totalSize = 필터된 개수")
            void track_filter_total_size() {
                save(26, Category.PROJECT, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.PROJECT, "B", Track.ENGINEERING, LocalDate.of(2026, 1, 2));
                save(26, Category.PROJECT, "C", Track.VISUALIZATION, LocalDate.of(2026, 1, 3));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.PROJECT, Track.ENGINEERING, null, null, 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(2);
                assertThat(response.getPosts()).hasSize(2);
            }

            @Test
            @DisplayName("TC-I-009 keyword 필터 적용 시 totalSize = 매칭된 개수")
            void keyword_filter_total_size() {
                save(26, Category.PROJECT, "추천 시스템", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.PROJECT, "이상 탐지", Track.ANALYSIS, LocalDate.of(2026, 1, 2));
                save(26, Category.PROJECT, "추천 알고리즘", Track.ENGINEERING, LocalDate.of(2026, 1, 3));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.PROJECT, Track.ALL, null, "추천", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(2);
                assertThat(response.getPosts()).hasSize(2);
            }
        }
    }

    // ══════════════════════════════════════════════
    // ARC-002 활동사진 조회 end-to-end
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("활동사진 조회 end-to-end (ARC-002)")
    class ActivitySearch {

        @Nested
        @DisplayName("[그룹 A] keyword 검색 동작")
        class KeywordSearch {

            @Test
            @DisplayName("TC-I-001 keyword 부분 일치 → 제목 중간에 검색어 포함 시 히트")
            void partial_match() {
                save(26, Category.ACTIVITY, "26기 워크샵 활동", Track.ALL, LocalDate.of(2026, 1, 1));
                save(26, Category.ACTIVITY, "MT 후기", Track.ALL, LocalDate.of(2026, 1, 2));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.ACTIVITY, null, null, "워크샵", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(1);
                assertThat(response.getPosts().get(0).getTitle()).isEqualTo("26기 워크샵 활동");
            }

            @Test
            @DisplayName("TC-I-002 keyword 대소문자 무시 → 영문 대소문자 관계 없이 히트")
            void case_insensitive() {
                save(26, Category.ACTIVITY, "BOAZ MT 2025", Track.ALL, LocalDate.of(2026, 1, 1));
                save(26, Category.ACTIVITY, "워크샵", Track.ALL, LocalDate.of(2026, 1, 2));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.ACTIVITY, null, null, "boaz", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(1);
                assertThat(response.getPosts().get(0).getTitle()).isEqualTo("BOAZ MT 2025");
            }

            @Test
            @DisplayName("TC-I-003 keyword 공백 무시 → 검색어의 공백 제거 후 매칭")
            void space_insensitive() {
                save(26, Category.ACTIVITY, "종강파티활동", Track.ALL, LocalDate.of(2026, 1, 1));
                save(26, Category.ACTIVITY, "MT후기", Track.ALL, LocalDate.of(2026, 1, 2));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.ACTIVITY, null, null, "종강 파티", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(1);
                assertThat(response.getPosts().get(0).getTitle()).isEqualTo("종강파티활동");
            }

            @Test
            @DisplayName("TC-I-004 keyword=\"\" → LIKE '%%' → 전체 반환")
            void empty_string_returns_all() {
                save(26, Category.ACTIVITY, "활동A", Track.ALL, LocalDate.of(2026, 1, 1));
                save(26, Category.ACTIVITY, "활동B", Track.ALL, LocalDate.of(2026, 1, 2));
                save(26, Category.ACTIVITY, "활동C", Track.ALL, LocalDate.of(2026, 1, 3));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.ACTIVITY, null, null, "", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(3);
            }
        }

        @Nested
        @DisplayName("[그룹 B] 정렬")
        class Sorting {

            @Test
            @DisplayName("TC-I-005 contentDate=null → 맨 뒤 정렬")
            void null_date_last() {
                save(26, Category.ACTIVITY, "A", Track.ALL, LocalDate.of(2026, 1, 1));
                save(26, Category.ACTIVITY, "B", Track.ALL, null);
                save(26, Category.ACTIVITY, "C", Track.ALL, LocalDate.of(2026, 3, 1));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.ACTIVITY, null, null, null, 1, 10);

                List<String> titles = response.getPosts().stream()
                        .map(p -> p.getTitle()).toList();
                assertThat(titles).containsExactly("C", "A", "B");
            }

            @Test
            @DisplayName("TC-I-006 contentDate 최신순 DESC 정렬")
            void date_desc() {
                save(26, Category.ACTIVITY, "A", Track.ALL, LocalDate.of(2025, 5, 1));
                save(26, Category.ACTIVITY, "B", Track.ALL, LocalDate.of(2026, 3, 1));
                save(26, Category.ACTIVITY, "C", Track.ALL, LocalDate.of(2024, 1, 1));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.ACTIVITY, null, null, null, 1, 10);

                List<String> titles = response.getPosts().stream()
                        .map(p -> p.getTitle()).toList();
                assertThat(titles).containsExactly("B", "A", "C");
            }

            @Test
            @DisplayName("TC-I-007 contentDate 동일할 경우, title ASC tie-break")
            void same_date_title_asc() {
                LocalDate sameDate = LocalDate.of(2026, 1, 1);
                save(26, Category.ACTIVITY, "나다라", Track.ALL, sameDate);
                save(26, Category.ACTIVITY, "가나다", Track.ALL, sameDate);
                save(26, Category.ACTIVITY, "다라마", Track.ALL, sameDate);

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.ACTIVITY, null, null, null, 1, 10);

                List<String> titles = response.getPosts().stream()
                        .map(p -> p.getTitle()).toList();
                assertThat(titles).containsExactly("가나다", "나다라", "다라마");
            }
        }

        @Nested
        @DisplayName("[그룹 C] 필터 후 totalSize")
        class FilterTotalSize {

            @Test
            @DisplayName("TC-I-008 term 필터 적용 시 totalSize = 필터된 개수")
            void term_filter_total_size() {
                save(25, Category.ACTIVITY, "A", Track.ALL, LocalDate.of(2025, 1, 1));
                save(25, Category.ACTIVITY, "B", Track.ALL, LocalDate.of(2025, 1, 2));
                save(26, Category.ACTIVITY, "C", Track.ALL, LocalDate.of(2026, 1, 1));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.ACTIVITY, null, 25, null, 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(2);
                assertThat(response.getPosts()).hasSize(2);
            }

            @Test
            @DisplayName("TC-I-009 keyword 필터 적용 시 totalSize = 매칭된 개수")
            void keyword_filter_total_size() {
                save(26, Category.ACTIVITY, "워크샵 후기", Track.ALL, LocalDate.of(2026, 1, 1));
                save(26, Category.ACTIVITY, "MT 일정", Track.ALL, LocalDate.of(2026, 1, 2));
                save(26, Category.ACTIVITY, "종강 파티 워크샵", Track.ALL, LocalDate.of(2026, 1, 3));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.ACTIVITY, null, null, "워크샵", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(2);
                assertThat(response.getPosts()).hasSize(2);
            }
        }
    }

    // ══════════════════════════════════════════════
    // ARC-003 기술블로그 조회 end-to-end
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("기술블로그 조회 end-to-end (ARC-003)")
    class BlogSearch {

        @Nested
        @DisplayName("[그룹 A] keyword 검색 동작")
        class KeywordSearch {

            @Test
            @DisplayName("TC-I-001 keyword 부분 일치 → 제목 중간에 검색어 포함 시 히트")
            void partial_match() {
                save(26, Category.BLOG, "Transformer 모델 구현기", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.BLOG, "데이터 전처리 가이드", Track.ANALYSIS, LocalDate.of(2026, 1, 2));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.BLOG, Track.ALL, null, "구현기", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(1);
                assertThat(response.getPosts().get(0).getTitle()).isEqualTo("Transformer 모델 구현기");
            }

            @Test
            @DisplayName("TC-I-002 keyword 대소문자 무시 → 영문 대소문자 관계 없이 히트")
            void case_insensitive() {
                save(26, Category.BLOG, "Transformer 분석", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.BLOG, "데이터 전처리", Track.ANALYSIS, LocalDate.of(2026, 1, 2));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.BLOG, Track.ALL, null, "transformer", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(1);
                assertThat(response.getPosts().get(0).getTitle()).isEqualTo("Transformer 분석");
            }

            @Test
            @DisplayName("TC-I-003 keyword 공백 무시 → 검색어의 공백 제거 후 매칭")
            void space_insensitive() {
                save(26, Category.BLOG, "딥러닝모델최적화", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.BLOG, "데이터전처리", Track.ANALYSIS, LocalDate.of(2026, 1, 2));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.BLOG, Track.ALL, null, "딥 러 닝", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(1);
                assertThat(response.getPosts().get(0).getTitle()).isEqualTo("딥러닝모델최적화");
            }

            @Test
            @DisplayName("TC-I-004 keyword=\"\" → LIKE '%%' → 전체 반환")
            void empty_string_returns_all() {
                save(26, Category.BLOG, "블로그A", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.BLOG, "블로그B", Track.ANALYSIS, LocalDate.of(2026, 1, 2));
                save(26, Category.BLOG, "블로그C", Track.VISUALIZATION, LocalDate.of(2026, 1, 3));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.BLOG, Track.ALL, null, "", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(3);
            }
        }

        @Nested
        @DisplayName("[그룹 B] 정렬")
        class Sorting {

            @Test
            @DisplayName("TC-I-005 contentDate=null → 맨 뒤 정렬")
            void null_date_last() {
                save(26, Category.BLOG, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.BLOG, "B", Track.ENGINEERING, null);
                save(26, Category.BLOG, "C", Track.ENGINEERING, LocalDate.of(2026, 3, 1));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.BLOG, Track.ALL, null, null, 1, 10);

                List<String> titles = response.getPosts().stream()
                        .map(p -> p.getTitle()).toList();
                assertThat(titles).containsExactly("C", "A", "B");
            }

            @Test
            @DisplayName("TC-I-006 contentDate 최신순 DESC 정렬")
            void date_desc() {
                save(26, Category.BLOG, "A", Track.ENGINEERING, LocalDate.of(2025, 5, 1));
                save(26, Category.BLOG, "B", Track.ENGINEERING, LocalDate.of(2026, 3, 1));
                save(26, Category.BLOG, "C", Track.ENGINEERING, LocalDate.of(2024, 1, 1));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.BLOG, Track.ALL, null, null, 1, 10);

                List<String> titles = response.getPosts().stream()
                        .map(p -> p.getTitle()).toList();
                assertThat(titles).containsExactly("B", "A", "C");
            }

            @Test
            @DisplayName("TC-I-007 contentDate 동일할 경우, title ASC tie-break")
            void same_date_title_asc() {
                LocalDate sameDate = LocalDate.of(2026, 1, 1);
                save(26, Category.BLOG, "나다라", Track.ENGINEERING, sameDate);
                save(26, Category.BLOG, "가나다", Track.ENGINEERING, sameDate);
                save(26, Category.BLOG, "다라마", Track.ENGINEERING, sameDate);

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.BLOG, Track.ALL, null, null, 1, 10);

                List<String> titles = response.getPosts().stream()
                        .map(p -> p.getTitle()).toList();
                assertThat(titles).containsExactly("가나다", "나다라", "다라마");
            }
        }

        @Nested
        @DisplayName("[그룹 C] 필터 후 totalSize")
        class FilterTotalSize {

            @Test
            @DisplayName("TC-I-008 track 필터 적용 시 totalSize = 필터된 개수")
            void track_filter_total_size() {
                save(26, Category.BLOG, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.BLOG, "B", Track.ENGINEERING, LocalDate.of(2026, 1, 2));
                save(26, Category.BLOG, "C", Track.VISUALIZATION, LocalDate.of(2026, 1, 3));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.BLOG, Track.ENGINEERING, null, null, 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(2);
                assertThat(response.getPosts()).hasSize(2);
            }

            @Test
            @DisplayName("TC-I-009 keyword 필터 적용 시 totalSize = 매칭된 개수")
            void keyword_filter_total_size() {
                save(26, Category.BLOG, "Transformer 구현기", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.BLOG, "데이터 전처리", Track.ANALYSIS, LocalDate.of(2026, 1, 2));
                save(26, Category.BLOG, "Transformer 최적화", Track.ENGINEERING, LocalDate.of(2026, 1, 3));

                ArchivePageResponse response = archiveService.searchArchives(
                        Category.BLOG, Track.ALL, null, "Transformer", 1, 10);

                assertThat(response.getTotalSize()).isEqualTo(2);
                assertThat(response.getPosts()).hasSize(2);
            }
        }
    }

    // ══════════════════════════════════════════════
    // ARC-004 기수 조회 end-to-end
    // ══════════════════════════════════════════════
    @Nested
    @DisplayName("기수 조회 end-to-end (ARC-004)")
    class GetTerms {

        @Nested
        @DisplayName("[그룹 A] DISTINCT")
        class Distinct {

            @Test
            @DisplayName("TC-I-001 같은 term인 데이터가 여러 개인 경우, 한 번만 반환")
            void dedup_same_term() {
                save(26, Category.BLOG, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.BLOG, "B", Track.ENGINEERING, LocalDate.of(2026, 1, 2));
                save(25, Category.BLOG, "C", Track.ENGINEERING, LocalDate.of(2025, 1, 1));

                ArchiveTermsResponse response = archiveService.getTerms();

                assertThat(response.terms()).hasSize(2);
                assertThat(response.terms().get(0).value()).isEqualTo(26);
                assertThat(response.terms().get(1).value()).isEqualTo(25);
            }

            @Test
            @DisplayName("TC-I-002 여러 category에 같은 term이 있는 경우, 한 번만 반환")
            void dedup_across_categories() {
                save(26, Category.PROJECT, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(26, Category.ACTIVITY, "B", Track.ALL, LocalDate.of(2026, 1, 1));
                save(26, Category.BLOG, "C", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(25, Category.PROJECT, "D", Track.ENGINEERING, LocalDate.of(2025, 1, 1));

                ArchiveTermsResponse response = archiveService.getTerms();

                assertThat(response.terms()).hasSize(2);
                assertThat(response.terms().get(0).value()).isEqualTo(26);
                assertThat(response.terms().get(1).value()).isEqualTo(25);
            }
        }

        @Nested
        @DisplayName("[그룹 B] 정렬")
        class Sorting {

            @Test
            @DisplayName("TC-I-003 term DESC 정렬 → 최신 기수 먼저")
            void term_desc() {
                save(24, Category.BLOG, "A", Track.ENGINEERING, LocalDate.of(2024, 1, 1));
                save(26, Category.BLOG, "B", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(25, Category.BLOG, "C", Track.ENGINEERING, LocalDate.of(2025, 1, 1));

                ArchiveTermsResponse response = archiveService.getTerms();

                assertThat(response.terms().get(0).value()).isEqualTo(26);
                assertThat(response.terms().get(1).value()).isEqualTo(25);
                assertThat(response.terms().get(2).value()).isEqualTo(24);
            }

            @Test
            @DisplayName("TC-I-004 term=0인 경우, 맨 뒤 정렬")
            void term_zero_last() {
                save(26, Category.BLOG, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                save(0, Category.BLOG, "B", Track.ENGINEERING, null);
                save(25, Category.BLOG, "C", Track.ENGINEERING, LocalDate.of(2025, 1, 1));

                ArchiveTermsResponse response = archiveService.getTerms();

                assertThat(response.terms().get(0).value()).isEqualTo(26);
                assertThat(response.terms().get(1).value()).isEqualTo(25);
                assertThat(response.terms().get(2).value()).isEqualTo(0);
                assertThat(response.terms().get(2).label()).isEqualTo("7기 이전");
            }
        }

        @Nested
        @DisplayName("[그룹 C] 빈 결과")
        class EmptyResult {

            @Test
            @DisplayName("TC-I-005 DB가 비어있는 경우, 빈 목록 반환")
            void empty_db() {
                ArchiveTermsResponse response = archiveService.getTerms();

                assertThat(response.terms()).isEmpty();
            }
        }
    }
}
