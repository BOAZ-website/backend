package com.boaz.backend.domain.admin.repository;

import com.boaz.backend.domain.admin.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByUsernameAndDeletedAtIsNull(String username);

    boolean existsByUsernameAndDeletedAtIsNull(String username);

    List<Admin> findAllByDeletedAtIsNullOrderByCreatedAtAsc();

    Optional<Admin> findByIdAndDeletedAtIsNull(Long id);
}
