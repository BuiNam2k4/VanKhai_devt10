package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.Subject;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    @Query("""
            select s from Subject s where :keyword = '' or lower(s.code) like :keyword
              or lower(s.name) like :keyword or lower(coalesce(s.description, '')) like :keyword
            """)
    Page<Subject> search(@Param("keyword") String keyword, Pageable pageable);

    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
