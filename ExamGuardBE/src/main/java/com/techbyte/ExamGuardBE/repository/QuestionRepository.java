package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.Question;
import com.techbyte.ExamGuardBE.enums.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    @EntityGraph(attributePaths = {"subject", "createdBy"})
    @Query("""
            select q from Question q
            where (:keyword = '' or lower(q.content) like :keyword)
              and (:subjectId is null or q.subject.id = :subjectId)
              and (:difficulty is null or q.difficulty = :difficulty)
              and (:type is null or q.type = :type)
              and (:active is null or q.active = :active)
              and (:authorId is null or q.createdBy.id = :authorId)
            """)
    Page<Question> search(@Param("keyword") String keyword, @Param("subjectId") Long subjectId,
                          @Param("difficulty") Difficulty difficulty, @Param("type") QuestionType type,
                          @Param("active") Boolean active, @Param("authorId") Long authorId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Question q where q.id = :id")
    Optional<Question> findForUpdate(@Param("id") Long id);

    @Query("select count(e) > 0 from ExamQuestion e where e.question.id = :id")
    boolean isUsedInExam(@Param("id") Long id);
}
