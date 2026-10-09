package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.User;
import com.techbyte.ExamGuardBE.enums.RoleName;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query(value = """
            select distinct u from User u left join u.roles r
            where (:keyword = '' or lower(u.username) like :keyword
                or lower(u.email) like :keyword or lower(u.fullName) like :keyword)
              and (:role is null or r.name = :role)
              and (:enabled is null or u.enabled = :enabled)
            """,
            countQuery = """
            select count(distinct u.id) from User u left join u.roles r
            where (:keyword = '' or lower(u.username) like :keyword
                or lower(u.email) like :keyword or lower(u.fullName) like :keyword)
              and (:role is null or r.name = :role)
              and (:enabled is null or u.enabled = :enabled)
            """)
    Page<User> search(@Param("keyword") String keyword, @Param("role") RoleName role,
                      @Param("enabled") Boolean enabled, Pageable pageable);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithRolesById(@Param("id") Long id);

    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
