package com.boaz.backend.domain.admin.repository;

import com.boaz.backend.domain.admin.entity.Admin;
import com.boaz.backend.global.common.enums.Track;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByUsernameAndDeletedAtIsNull(String username);

    boolean existsByUsernameAndDeletedAtIsNull(String username);

    List<Admin> findAllByDeletedAtIsNullOrderByCreatedAtAsc();

    Optional<Admin> findByIdAndDeletedAtIsNull(Long id);
    
    /**
     * 살아 있는 계정 전원을 <b>잠그면서</b> 읽는다 ({@code SELECT ... FOR UPDATE}).
     *
     * <p>락아웃 검사가 지키는 규칙("계정 관리자가 최소 한 명")은 행 하나가 아니라 <b>집합</b>에 걸린
     * 조건이라, 대상 행만 잠그면 서로 다른 계정을 삭제하는 두 요청이 동시에 들어올 경우 문제가 생긴다.
     * "나 말고 한 명 더 있네"를 읽고 둘 다 통과해 보유자가 0이 된다. 잠글 것은 <b>세는 집합 전체</b>다.
     *
     * <p><b>데드락을 막기 위해 한 번에 잡는다.</b> 대상 행을 먼저 잡고 집합을 나중에 잡는
     * 식으로 나누면 대상이 요청마다 달라 순서가 엇갈린다(A→전체 / B→전체). 이 호출 하나가 축소 경로의
     * 유일한 락 획득 지점이라 이 구간은 사실상 뮤텍스이고, 먼저 들어간 쪽이 커밋할 때까지 나머지는 대기한다.
     *
     * <p><b>트랜잭션의 첫 DB 접근이어야 한다. 이건 Stale Read 문제로, 위와 별개다.</b> 앞에서 대상을
     * 평범하게 한 번 읽어 두면 이 조회가 최신 행을 가져와도 영속성 컨텍스트가 먼저 만든 객체를 돌려준다.
     * 다른 계정은 최신인데 대상만 낡아서, 가드가 "이 계정이 지금 권한을 갖고 있나"를 옛 값으로 판단한다.
     * 그래서 대상도 이 목록에서 꺼낸다.
     *
     * <p>{@code deleted_at} 에 인덱스가 없어 훑은 행 전부에 락이 걸린다. 조건이 사실상 전체 행에
     * 해당하므로 인덱스를 걸어도 범위는 거의 안 줄고, 계정이 수십 단위라 실측 비용이 없어 그대로 둔다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Admin a WHERE a.deletedAt IS NULL")
    List<Admin> findAllLiveForUpdate();

    // 지원서별 평가 조회 — 해당 부문 평가자 풀 (미평가자 null 포함용)
    List<Admin> findByTrackAndDeletedAtIsNullOrderByNameAsc(Track track);

    // 평가자 풀 = 해당 부문 + 전 부문 평가 권한자(차기 대표진). 차기 대표진은 본인 track과 무관하게 모든 지원자 풀에 포함.
    @Query("SELECT DISTINCT a FROM Admin a WHERE a.deletedAt IS NULL " +
           "AND (a.track = :track OR (a.role = :allTrackRole AND a.teamName = :allTrackTeam)) " +
           "ORDER BY a.name ASC")
    List<Admin> findEvaluatorPool(@Param("track") Track track,
                                  @Param("allTrackRole") Admin.Role allTrackRole,
                                  @Param("allTrackTeam") Admin.TeamName allTrackTeam);
}
