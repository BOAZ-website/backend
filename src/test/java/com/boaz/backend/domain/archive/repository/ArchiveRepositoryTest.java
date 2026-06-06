package com.boaz.backend.domain.archive.repository;

import com.boaz.backend.domain.archive.entity.Archive;
import com.boaz.backend.domain.archive.entity.Archive.Category;
import com.boaz.backend.global.common.enums.Track;
import com.boaz.backend.global.config.JpaConfig;
import com.boaz.backend.support.TestcontainersBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class ArchiveRepositoryTest extends TestcontainersBase {

    @Autowired ArchiveRepository archiveRepository;
    @Autowired TestEntityManager em;

    private Archive buildArchive(int term, Category category, String title, Track track, LocalDate contentDate) {
        return Archive.builder()
                .term(term).category(category).title(title)
                .track(track).imageUrl("http://img.example.com/test.jpg")
                .links("{}").contentDate(contentDate)
                .build();
    }

    private void saveAll(Archive... archives) {
        for (Archive a : archives) {
            em.persist(a);
        }
        em.flush();
        em.clear();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ARC-001 searchArchives - PROJECT 통합 테스트
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("ARC-001 searchArchives(PROJECT) 통합 테스트")
    class ProjectSearchIntegration {

        @Nested
        @DisplayName("[그룹 A] keyword 검색 동작")
        class KeywordSearch {

            @Test
            @DisplayName("TC-I-001 keyword 부분 일치 → 제목 중간에 검색어 포함 시 히트")
            void partial_match() {
                saveAll(
                        buildArchive(26, Category.PROJECT, "실시간 추천 시스템 구축", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.PROJECT, "이상 탐지 모델 개발", Track.ANALYSIS, LocalDate.of(2026, 1, 2))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.PROJECT, null, null, "추천", PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getTitle()).isEqualTo("실시간 추천 시스템 구축");
            }

            @Test
            @DisplayName("TC-I-002 keyword 대소문자 무시 → 영문 대소문자 관계 없이 히트")
            void case_insensitive() {
                saveAll(
                        buildArchive(26, Category.PROJECT, "BOAZ Project", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.PROJECT, "이상 탐지", Track.ANALYSIS, LocalDate.of(2026, 1, 2))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.PROJECT, null, null, "boaz", PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getTitle()).isEqualTo("BOAZ Project");
            }

            @Test
            @DisplayName("TC-I-003 keyword 공백 무시 → 검색어의 공백 제거 후 매칭")
            void space_insensitive() {
                saveAll(
                        buildArchive(26, Category.PROJECT, "딥러닝프로젝트", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.PROJECT, "이상탐지", Track.ANALYSIS, LocalDate.of(2026, 1, 2))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.PROJECT, null, null, "딥 러 닝", PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getTitle()).isEqualTo("딥러닝프로젝트");
            }

            @Test
            @DisplayName("TC-I-004 keyword=\"\" → LIKE '%%' → 전체 반환")
            void empty_string_returns_all() {
                saveAll(
                        buildArchive(26, Category.PROJECT, "프로젝트A", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.PROJECT, "프로젝트B", Track.ANALYSIS, LocalDate.of(2026, 1, 2)),
                        buildArchive(26, Category.PROJECT, "프로젝트C", Track.VISUALIZATION, LocalDate.of(2026, 1, 3))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.PROJECT, null, null, "", PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(3);
            }
        }

        @Nested
        @DisplayName("[그룹 B] 정렬")
        class Sorting {

            @Test
            @DisplayName("TC-I-005 contentDate=null → 맨 뒤 정렬")
            void null_date_last() {
                Archive a1 = buildArchive(26, Category.PROJECT, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                Archive a2 = buildArchive(26, Category.PROJECT, "B", Track.ENGINEERING, null);
                Archive a3 = buildArchive(26, Category.PROJECT, "C", Track.ENGINEERING, LocalDate.of(2026, 3, 1));
                em.persist(a1); em.persist(a2); em.persist(a3);
                em.flush(); em.clear();

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.PROJECT, null, null, null, PageRequest.of(0, 10));

                List<String> titles = result.getContent().stream().map(Archive::getTitle).toList();
                assertThat(titles).containsExactly("C", "A", "B");
            }

            @Test
            @DisplayName("TC-I-006 contentDate DESC 정렬 → 최신순")
            void date_desc() {
                Archive a1 = buildArchive(26, Category.PROJECT, "A", Track.ENGINEERING, LocalDate.of(2025, 5, 1));
                Archive a2 = buildArchive(26, Category.PROJECT, "B", Track.ENGINEERING, LocalDate.of(2026, 3, 1));
                Archive a3 = buildArchive(26, Category.PROJECT, "C", Track.ENGINEERING, LocalDate.of(2024, 1, 1));
                em.persist(a1); em.persist(a2); em.persist(a3);
                em.flush(); em.clear();

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.PROJECT, null, null, null, PageRequest.of(0, 10));

                List<String> titles = result.getContent().stream().map(Archive::getTitle).toList();
                assertThat(titles).containsExactly("B", "A", "C");
            }

            @Test
            @DisplayName("TC-I-007 contentDate 동일 → title ASC tie-break")
            void same_date_title_asc() {
                LocalDate sameDate = LocalDate.of(2026, 1, 1);
                Archive a1 = buildArchive(26, Category.PROJECT, "나다라", Track.ENGINEERING, sameDate);
                Archive a2 = buildArchive(26, Category.PROJECT, "가나다", Track.ENGINEERING, sameDate);
                Archive a3 = buildArchive(26, Category.PROJECT, "다라마", Track.ENGINEERING, sameDate);
                em.persist(a1); em.persist(a2); em.persist(a3);
                em.flush(); em.clear();

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.PROJECT, null, null, null, PageRequest.of(0, 10));

                List<String> titles = result.getContent().stream().map(Archive::getTitle).toList();
                assertThat(titles).containsExactly("가나다", "나다라", "다라마");
            }
        }

        @Nested
        @DisplayName("[그룹 C] 필터 후 totalSize")
        class FilterTotalSize {

            @Test
            @DisplayName("TC-I-008 track 필터 적용 시 totalSize = 필터된 개수")
            void track_filter_total_size() {
                saveAll(
                        buildArchive(26, Category.PROJECT, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.PROJECT, "B", Track.ENGINEERING, LocalDate.of(2026, 1, 2)),
                        buildArchive(26, Category.PROJECT, "C", Track.VISUALIZATION, LocalDate.of(2026, 1, 3))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.PROJECT, Track.ENGINEERING, null, null, PageRequest.of(0, 10));

                assertThat(result.getTotalElements()).isEqualTo(2);
                assertThat(result.getContent()).hasSize(2);
            }

            @Test
            @DisplayName("TC-I-009 keyword 필터 적용 시 totalSize = 매칭된 개수")
            void keyword_filter_total_size() {
                saveAll(
                        buildArchive(26, Category.PROJECT, "추천 시스템", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.PROJECT, "이상 탐지", Track.ANALYSIS, LocalDate.of(2026, 1, 2)),
                        buildArchive(26, Category.PROJECT, "추천 알고리즘", Track.ENGINEERING, LocalDate.of(2026, 1, 3))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.PROJECT, null, null, "추천", PageRequest.of(0, 10));

                assertThat(result.getTotalElements()).isEqualTo(2);
                assertThat(result.getContent()).hasSize(2);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ARC-002 searchArchives - ACTIVITY 통합 테스트
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("ARC-002 searchArchives(ACTIVITY) 통합 테스트")
    class ActivitySearchIntegration {

        @Nested
        @DisplayName("[그룹 A] keyword 검색 동작")
        class KeywordSearch {

            @Test
            @DisplayName("TC-I-001 keyword 부분 일치 → 제목 중간에 검색어 포함 시 히트")
            void partial_match() {
                saveAll(
                        buildArchive(26, Category.ACTIVITY, "26기 워크샵 활동", Track.ALL, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.ACTIVITY, "MT 후기", Track.ALL, LocalDate.of(2026, 1, 2))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.ACTIVITY, null, null, "워크샵", PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getTitle()).isEqualTo("26기 워크샵 활동");
            }

            @Test
            @DisplayName("TC-I-002 keyword 대소문자 무시 → 영문 대소문자 관계 없이 히트")
            void case_insensitive() {
                saveAll(
                        buildArchive(26, Category.ACTIVITY, "BOAZ MT 2025", Track.ALL, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.ACTIVITY, "워크샵", Track.ALL, LocalDate.of(2026, 1, 2))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.ACTIVITY, null, null, "boaz", PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getTitle()).isEqualTo("BOAZ MT 2025");
            }

            @Test
            @DisplayName("TC-I-003 keyword 공백 무시 → 검색어의 공백 제거 후 매칭")
            void space_insensitive() {
                saveAll(
                        buildArchive(26, Category.ACTIVITY, "종강파티활동", Track.ALL, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.ACTIVITY, "MT후기", Track.ALL, LocalDate.of(2026, 1, 2))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.ACTIVITY, null, null, "종강 파티", PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getTitle()).isEqualTo("종강파티활동");
            }

            @Test
            @DisplayName("TC-I-004 keyword=\"\" → LIKE '%%' → 전체 반환")
            void empty_string_returns_all() {
                saveAll(
                        buildArchive(26, Category.ACTIVITY, "활동A", Track.ALL, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.ACTIVITY, "활동B", Track.ALL, LocalDate.of(2026, 1, 2)),
                        buildArchive(26, Category.ACTIVITY, "활동C", Track.ALL, LocalDate.of(2026, 1, 3))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.ACTIVITY, null, null, "", PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(3);
            }
        }

        @Nested
        @DisplayName("[그룹 B] 정렬")
        class Sorting {

            @Test
            @DisplayName("TC-I-005 contentDate=null → 맨 뒤 정렬")
            void null_date_last() {
                Archive a1 = buildArchive(26, Category.ACTIVITY, "A", Track.ALL, LocalDate.of(2026, 1, 1));
                Archive a2 = buildArchive(26, Category.ACTIVITY, "B", Track.ALL, null);
                Archive a3 = buildArchive(26, Category.ACTIVITY, "C", Track.ALL, LocalDate.of(2026, 3, 1));
                em.persist(a1); em.persist(a2); em.persist(a3);
                em.flush(); em.clear();

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.ACTIVITY, null, null, null, PageRequest.of(0, 10));

                List<String> titles = result.getContent().stream().map(Archive::getTitle).toList();
                assertThat(titles).containsExactly("C", "A", "B");
            }

            @Test
            @DisplayName("TC-I-006 contentDate DESC 정렬 → 최신순")
            void date_desc() {
                Archive a1 = buildArchive(26, Category.ACTIVITY, "A", Track.ALL, LocalDate.of(2025, 5, 1));
                Archive a2 = buildArchive(26, Category.ACTIVITY, "B", Track.ALL, LocalDate.of(2026, 3, 1));
                Archive a3 = buildArchive(26, Category.ACTIVITY, "C", Track.ALL, LocalDate.of(2024, 1, 1));
                em.persist(a1); em.persist(a2); em.persist(a3);
                em.flush(); em.clear();

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.ACTIVITY, null, null, null, PageRequest.of(0, 10));

                List<String> titles = result.getContent().stream().map(Archive::getTitle).toList();
                assertThat(titles).containsExactly("B", "A", "C");
            }

            @Test
            @DisplayName("TC-I-007 contentDate 동일 → title ASC tie-break")
            void same_date_title_asc() {
                LocalDate sameDate = LocalDate.of(2026, 1, 1);
                Archive a1 = buildArchive(26, Category.ACTIVITY, "나다라", Track.ALL, sameDate);
                Archive a2 = buildArchive(26, Category.ACTIVITY, "가나다", Track.ALL, sameDate);
                Archive a3 = buildArchive(26, Category.ACTIVITY, "다라마", Track.ALL, sameDate);
                em.persist(a1); em.persist(a2); em.persist(a3);
                em.flush(); em.clear();

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.ACTIVITY, null, null, null, PageRequest.of(0, 10));

                List<String> titles = result.getContent().stream().map(Archive::getTitle).toList();
                assertThat(titles).containsExactly("가나다", "나다라", "다라마");
            }
        }

        @Nested
        @DisplayName("[그룹 C] 필터 후 totalSize")
        class FilterTotalSize {

            @Test
            @DisplayName("TC-I-008 term 필터 적용 시 totalSize = 필터된 개수")
            void term_filter_total_size() {
                saveAll(
                        buildArchive(25, Category.ACTIVITY, "A", Track.ALL, LocalDate.of(2025, 1, 1)),
                        buildArchive(25, Category.ACTIVITY, "B", Track.ALL, LocalDate.of(2025, 1, 2)),
                        buildArchive(26, Category.ACTIVITY, "C", Track.ALL, LocalDate.of(2026, 1, 1))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.ACTIVITY, null, 25, null, PageRequest.of(0, 10));

                assertThat(result.getTotalElements()).isEqualTo(2);
                assertThat(result.getContent()).hasSize(2);
            }

            @Test
            @DisplayName("TC-I-009 keyword 필터 적용 시 totalSize = 매칭된 개수")
            void keyword_filter_total_size() {
                saveAll(
                        buildArchive(26, Category.ACTIVITY, "워크샵 후기", Track.ALL, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.ACTIVITY, "MT 일정", Track.ALL, LocalDate.of(2026, 1, 2)),
                        buildArchive(26, Category.ACTIVITY, "종강 파티 워크샵", Track.ALL, LocalDate.of(2026, 1, 3))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.ACTIVITY, null, null, "워크샵", PageRequest.of(0, 10));

                assertThat(result.getTotalElements()).isEqualTo(2);
                assertThat(result.getContent()).hasSize(2);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ARC-003 searchArchives - BLOG 통합 테스트
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("ARC-003 searchArchives(BLOG) 통합 테스트")
    class BlogSearchIntegration {

        @Nested
        @DisplayName("[그룹 A] keyword 검색 동작")
        class KeywordSearch {

            @Test
            @DisplayName("TC-I-001 keyword 부분 일치 → 제목 중간에 검색어 포함 시 히트")
            void partial_match() {
                saveAll(
                        buildArchive(26, Category.BLOG, "Transformer 모델 구현기", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.BLOG, "데이터 전처리 가이드", Track.ANALYSIS, LocalDate.of(2026, 1, 2))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.BLOG, null, null, "구현기", PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getTitle()).isEqualTo("Transformer 모델 구현기");
            }

            @Test
            @DisplayName("TC-I-002 keyword 대소문자 무시 → 영문 대소문자 관계 없이 히트")
            void case_insensitive() {
                saveAll(
                        buildArchive(26, Category.BLOG, "Transformer 분석", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.BLOG, "데이터 전처리", Track.ANALYSIS, LocalDate.of(2026, 1, 2))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.BLOG, null, null, "transformer", PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getTitle()).isEqualTo("Transformer 분석");
            }

            @Test
            @DisplayName("TC-I-003 keyword 공백 무시 → 검색어의 공백 제거 후 매칭")
            void space_insensitive() {
                saveAll(
                        buildArchive(26, Category.BLOG, "딥러닝모델최적화", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.BLOG, "데이터전처리", Track.ANALYSIS, LocalDate.of(2026, 1, 2))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.BLOG, null, null, "딥 러 닝", PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getTitle()).isEqualTo("딥러닝모델최적화");
            }

            @Test
            @DisplayName("TC-I-004 keyword=\"\" → LIKE '%%' → 전체 반환")
            void empty_string_returns_all() {
                saveAll(
                        buildArchive(26, Category.BLOG, "블로그A", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.BLOG, "블로그B", Track.ANALYSIS, LocalDate.of(2026, 1, 2)),
                        buildArchive(26, Category.BLOG, "블로그C", Track.VISUALIZATION, LocalDate.of(2026, 1, 3))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.BLOG, null, null, "", PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(3);
            }
        }

        @Nested
        @DisplayName("[그룹 B] 정렬")
        class Sorting {

            @Test
            @DisplayName("TC-I-005 contentDate=null → 맨 뒤 정렬")
            void null_date_last() {
                Archive a1 = buildArchive(26, Category.BLOG, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1));
                Archive a2 = buildArchive(26, Category.BLOG, "B", Track.ENGINEERING, null);
                Archive a3 = buildArchive(26, Category.BLOG, "C", Track.ENGINEERING, LocalDate.of(2026, 3, 1));
                em.persist(a1); em.persist(a2); em.persist(a3);
                em.flush(); em.clear();

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.BLOG, null, null, null, PageRequest.of(0, 10));

                List<String> titles = result.getContent().stream().map(Archive::getTitle).toList();
                assertThat(titles).containsExactly("C", "A", "B");
            }

            @Test
            @DisplayName("TC-I-006 contentDate DESC 정렬 → 최신순")
            void date_desc() {
                Archive a1 = buildArchive(26, Category.BLOG, "A", Track.ENGINEERING, LocalDate.of(2025, 5, 1));
                Archive a2 = buildArchive(26, Category.BLOG, "B", Track.ENGINEERING, LocalDate.of(2026, 3, 1));
                Archive a3 = buildArchive(26, Category.BLOG, "C", Track.ENGINEERING, LocalDate.of(2024, 1, 1));
                em.persist(a1); em.persist(a2); em.persist(a3);
                em.flush(); em.clear();

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.BLOG, null, null, null, PageRequest.of(0, 10));

                List<String> titles = result.getContent().stream().map(Archive::getTitle).toList();
                assertThat(titles).containsExactly("B", "A", "C");
            }

            @Test
            @DisplayName("TC-I-007 contentDate 동일 → title ASC tie-break")
            void same_date_title_asc() {
                LocalDate sameDate = LocalDate.of(2026, 1, 1);
                Archive a1 = buildArchive(26, Category.BLOG, "나다라", Track.ENGINEERING, sameDate);
                Archive a2 = buildArchive(26, Category.BLOG, "가나다", Track.ENGINEERING, sameDate);
                Archive a3 = buildArchive(26, Category.BLOG, "다라마", Track.ENGINEERING, sameDate);
                em.persist(a1); em.persist(a2); em.persist(a3);
                em.flush(); em.clear();

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.BLOG, null, null, null, PageRequest.of(0, 10));

                List<String> titles = result.getContent().stream().map(Archive::getTitle).toList();
                assertThat(titles).containsExactly("가나다", "나다라", "다라마");
            }
        }

        @Nested
        @DisplayName("[그룹 C] 필터 후 totalSize")
        class FilterTotalSize {

            @Test
            @DisplayName("TC-I-008 track 필터 적용 시 totalSize = 필터된 개수")
            void track_filter_total_size() {
                saveAll(
                        buildArchive(26, Category.BLOG, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.BLOG, "B", Track.ENGINEERING, LocalDate.of(2026, 1, 2)),
                        buildArchive(26, Category.BLOG, "C", Track.VISUALIZATION, LocalDate.of(2026, 1, 3))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.BLOG, Track.ENGINEERING, null, null, PageRequest.of(0, 10));

                assertThat(result.getTotalElements()).isEqualTo(2);
                assertThat(result.getContent()).hasSize(2);
            }

            @Test
            @DisplayName("TC-I-009 keyword 필터 적용 시 totalSize = 매칭된 개수")
            void keyword_filter_total_size() {
                saveAll(
                        buildArchive(26, Category.BLOG, "Transformer 구현기", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.BLOG, "데이터 전처리", Track.ANALYSIS, LocalDate.of(2026, 1, 2)),
                        buildArchive(26, Category.BLOG, "Transformer 최적화", Track.ENGINEERING, LocalDate.of(2026, 1, 3))
                );

                Page<Archive> result = archiveRepository.searchArchives(
                        Category.BLOG, null, null, "Transformer", PageRequest.of(0, 10));

                assertThat(result.getTotalElements()).isEqualTo(2);
                assertThat(result.getContent()).hasSize(2);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ARC-004 findDistinctTermsOrdered 통합 테스트
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("ARC-004 findDistinctTermsOrdered 통합 테스트")
    class FindDistinctTermsOrdered {

        @Nested
        @DisplayName("[그룹 A] DISTINCT")
        class Distinct {

            @Test
            @DisplayName("TC-I-001 같은 term인 데이터가 여러 개인 경우, 한 번만 반환")
            void dedup_same_term() {
                saveAll(
                        buildArchive(26, Category.BLOG, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.BLOG, "B", Track.ENGINEERING, LocalDate.of(2026, 1, 2)),
                        buildArchive(25, Category.BLOG, "C", Track.ENGINEERING, LocalDate.of(2025, 1, 1))
                );

                List<Integer> result = archiveRepository.findDistinctTermsOrdered();

                assertThat(result).hasSize(2);
                assertThat(result).containsExactly(26, 25);
            }

            @Test
            @DisplayName("TC-I-002 여러 category에 같은 term이 있는 경우, 한 번만 반환")
            void dedup_across_categories() {
                saveAll(
                        buildArchive(26, Category.PROJECT, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.ACTIVITY, "B", Track.ALL, LocalDate.of(2026, 1, 1)),
                        buildArchive(26, Category.BLOG, "C", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(25, Category.PROJECT, "D", Track.ENGINEERING, LocalDate.of(2025, 1, 1))
                );

                List<Integer> result = archiveRepository.findDistinctTermsOrdered();

                assertThat(result).hasSize(2);
                assertThat(result).containsExactly(26, 25);
            }
        }

        @Nested
        @DisplayName("[그룹 B] 정렬")
        class Sorting {

            @Test
            @DisplayName("TC-I-003 term DESC 정렬 → 최신 기수 먼저")
            void term_desc() {
                saveAll(
                        buildArchive(24, Category.BLOG, "A", Track.ENGINEERING, LocalDate.of(2024, 1, 1)),
                        buildArchive(26, Category.BLOG, "B", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(25, Category.BLOG, "C", Track.ENGINEERING, LocalDate.of(2025, 1, 1))
                );

                List<Integer> result = archiveRepository.findDistinctTermsOrdered();

                assertThat(result).containsExactly(26, 25, 24);
            }

            @Test
            @DisplayName("TC-I-004 term=0 인 경우, 맨 뒤 정렬")
            void term_zero_last() {
                saveAll(
                        buildArchive(26, Category.BLOG, "A", Track.ENGINEERING, LocalDate.of(2026, 1, 1)),
                        buildArchive(0, Category.BLOG, "B", Track.ENGINEERING, null),
                        buildArchive(25, Category.BLOG, "C", Track.ENGINEERING, LocalDate.of(2025, 1, 1))
                );

                List<Integer> result = archiveRepository.findDistinctTermsOrdered();

                assertThat(result).containsExactly(26, 25, 0);
            }
        }

        @Nested
        @DisplayName("[그룹 C] 빈 결과")
        class EmptyResult {

            @Test
            @DisplayName("TC-I-005 DB가 비어있는 경우, 빈 목록 반환")
            void empty_db() {
                List<Integer> result = archiveRepository.findDistinctTermsOrdered();

                assertThat(result).isEmpty();
            }
        }
    }
}
