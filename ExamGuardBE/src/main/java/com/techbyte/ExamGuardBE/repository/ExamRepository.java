package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.Exam;
import com.techbyte.ExamGuardBE.enums.ExamStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    @EntityGraph(attributePaths = {"subject", "teacher"})
    @Query("""
            select e from Exam e where
              (:keyword = '' or lower(e.title) like :keyword or lower(e.description) like :keyword)
              and (:subjectId is null or e.subject.id = :subjectId)
              and (:status is null or e.status = :status)
              and (:teacherId is null or e.teacher.id = :teacherId)
            """)
    Page<Exam> search(@Param("keyword") String keyword, @Param("subjectId") Long subjectId,
                      @Param("status") ExamStatus status, @Param("teacherId") Long teacherId, Pageable pageable);

    @EntityGraph(attributePaths = "classes")
    @Query("select distinct e from Exam e where e.id in :ids")
    List<Exam> findWithClassesByIdIn(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Exam e where e.id = :id")
    Optional<Exam> findForUpdate(@Param("id") Long id);

    @Query("select count(s) > 0 from ExamSession s where s.exam.id = :id")
    boolean hasSessions(@Param("id") Long id);
}
