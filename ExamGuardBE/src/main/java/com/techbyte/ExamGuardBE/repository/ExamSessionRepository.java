package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.ExamSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ExamSession s where s.id = :id")
    Optional<ExamSession> findForUpdate(@Param("id") Long id);
}
