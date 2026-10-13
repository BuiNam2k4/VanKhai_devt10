package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select o from QuestionOption o where o.question.id in :ids order by o.displayOrder")
    List<QuestionOption> findForSnapshot(@Param("ids") Collection<Long> ids);
    List<QuestionOption> findByQuestionIdOrderByDisplayOrderAsc(Long questionId);
    List<QuestionOption> findByQuestionIdInOrderByDisplayOrderAsc(Collection<Long> questionIds);
}
