package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.SessionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, Long> {
    List<SessionQuestion> findBySessionIdOrderByDisplayOrderAsc(Long sessionId);
}
