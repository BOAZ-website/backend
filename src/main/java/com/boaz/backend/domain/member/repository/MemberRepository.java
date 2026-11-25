package com.boaz.backend.domain.member.repository;

import com.boaz.backend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
