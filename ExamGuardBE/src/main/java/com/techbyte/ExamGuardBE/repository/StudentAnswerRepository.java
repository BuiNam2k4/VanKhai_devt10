package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.StudentAnswer;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, Long> {
    @Query("select a from StudentAnswer a join fetch a.sessionQuestion q where q.session.id = :sessionId order by q.displayOrder")
    List<StudentAnswer> findForSession(@Param("sessionId") Long sessionId);
}
