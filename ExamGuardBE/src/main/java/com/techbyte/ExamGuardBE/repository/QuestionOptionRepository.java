package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {
    List<QuestionOption> findByQuestionIdOrderByDisplayOrderAsc(Long questionId);
    List<QuestionOption> findByQuestionIdInOrderByDisplayOrderAsc(Collection<Long> questionIds);
}
