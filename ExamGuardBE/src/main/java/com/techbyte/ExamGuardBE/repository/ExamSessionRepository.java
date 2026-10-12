package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.ExamSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {
    @Query("select s.exam.id as examId, count(s) as attemptsUsed from ExamSession s where s.student.id = :studentId and s.exam.id in :ids group by s.exam.id")
    List<AttemptCount> countStudentAttempts(@Param("studentId") Long studentId, @Param("ids") Collection<Long> ids);

    @Query("select s from ExamSession s where s.student.id = :studentId and s.exam.id in :ids and s.status = com.techbyte.ExamGuardBE.enums.SessionStatus.IN_PROGRESS")
    List<ExamSession> findActiveStudentAttempts(@Param("studentId") Long studentId, @Param("ids") Collection<Long> ids);

    interface AttemptCount { Long getExamId(); long getAttemptsUsed(); }
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ExamSession s where s.id = :id")
    Optional<ExamSession> findForUpdate(@Param("id") Long id);
}
