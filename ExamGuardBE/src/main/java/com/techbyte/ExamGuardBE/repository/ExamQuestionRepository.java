package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.ExamQuestion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {
    @Query("select eq.question.id from ExamQuestion eq where eq.exam.id = :id order by eq.question.id")
    List<Long> findQuestionIdsByExamId(@Param("id") Long id);
    @EntityGraph(attributePaths = {"question", "question.subject"})
    List<ExamQuestion> findByExamIdOrderByDisplayOrderAsc(Long examId);
    boolean existsByExamId(Long examId);

    @Query("select eq.exam.id as examId, count(eq) as questionCount from ExamQuestion eq where eq.exam.id in :ids group by eq.exam.id")
    List<ExamQuestionCount> countByExamIds(@Param("ids") Collection<Long> ids);

    interface ExamQuestionCount {
        Long getExamId();
        long getQuestionCount();
    }
}
