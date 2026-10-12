package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.Exam;
import com.techbyte.ExamGuardBE.enums.ExamStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.time.LocalDateTime;
import com.techbyte.ExamGuardBE.entity.Subject;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    @EntityGraph(attributePaths = {"subject", "teacher"})
    @Query("""
        select e from Exam e where e.status in (com.techbyte.ExamGuardBE.enums.ExamStatus.PUBLISHED, com.techbyte.ExamGuardBE.enums.ExamStatus.CLOSED)
        and exists (select c.id from Exam assigned join assigned.classes c join c.students s where assigned.id = e.id and s.id = :studentId)
        and (:keyword = '' or lower(e.title) like :keyword or lower(e.description) like :keyword or lower(e.subject.name) like :keyword or lower(e.subject.code) like :keyword)
        and (:subjectId is null or e.subject.id = :subjectId) and (:status is null or e.status = :status)
        and (:window is null or (:window = 'UPCOMING' and e.startsAt > :now)
             or (:window = 'OPEN' and e.startsAt <= :now and e.endsAt > :now)
             or (:window = 'ENDED' and e.endsAt <= :now))
        """)
    Page<Exam> searchForStudent(@Param("studentId") Long studentId, @Param("keyword") String keyword,
        @Param("subjectId") Long subjectId, @Param("status") ExamStatus status, @Param("window") String window,
        @Param("now") LocalDateTime now, Pageable pageable);

    @EntityGraph(attributePaths = {"subject", "teacher"})
    @Query("""
        select e from Exam e where e.id = :id
        and e.status in (com.techbyte.ExamGuardBE.enums.ExamStatus.PUBLISHED, com.techbyte.ExamGuardBE.enums.ExamStatus.CLOSED)
        and exists (select c.id from Exam assigned join assigned.classes c join c.students s where assigned.id = e.id and s.id = :studentId)
        """)
    Optional<Exam> findVisibleToStudent(@Param("id") Long id, @Param("studentId") Long studentId);

    @Query("""
        select subject from Subject subject where exists (
        select e.id from Exam e join e.classes c join c.students s where s.id = :studentId and e.subject.id = subject.id
        and e.status in (com.techbyte.ExamGuardBE.enums.ExamStatus.PUBLISHED, com.techbyte.ExamGuardBE.enums.ExamStatus.CLOSED))
        """)
    Page<Subject> findSubjectsForStudent(@Param("studentId") Long studentId, Pageable pageable);

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
