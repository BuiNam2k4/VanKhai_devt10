package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.SessionQuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SessionQuestionOptionRepository extends JpaRepository<SessionQuestionOption, Long> {
    List<SessionQuestionOption> findBySessionQuestionIdOrderByDisplayOrderAsc(Long questionId);
    List<SessionQuestionOption> findBySessionQuestionIdInOrderByDisplayOrderAsc(Collection<Long> ids);
}
