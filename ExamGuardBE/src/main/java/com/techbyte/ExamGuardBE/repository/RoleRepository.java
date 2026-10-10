package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.Role;
import com.techbyte.ExamGuardBE.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleName name);
    List<Role> findByNameIn(Set<RoleName> names);
}
