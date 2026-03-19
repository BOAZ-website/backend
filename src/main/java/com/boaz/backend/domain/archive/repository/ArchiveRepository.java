package com.boaz.backend.domain.archive.repository;

import com.boaz.backend.domain.archive.entity.Archive;
import com.boaz.backend.domain.archive.entity.ArchiveCategory;
import com.boaz.backend.global.common.enums.Track;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


// 메서드 호출 시 JPQL 쿼리 실행 
public interface ArchiveRepository extends JpaRepository<Archive, Long> {

    @Query("""
        SELECT a 
        FROM Archive a
        WHERE a.category = :category
            AND (:track IS NULL OR a.track = :track)
            AND (:term IS NULL OR a.term = :term)
            AND (
                :keyword IS NULL 
                OR LOWER(REPLACE(a.title, ' ', '')) 
                    LIKE CONCAT('%', LOWER(REPLACE(:keyword, ' ', '')), '%')
            )
        ORDER BY a.contentDate DESC, a.id DESC 
    """)
    Page<Archive> searchArchives(
        @Param("category") ArchiveCategory category, 
        @Param("track") Track track, 
        @Param("term") Integer term, 
        @Param("keyword") String keyword, 
        Pageable pageable
    );
}
