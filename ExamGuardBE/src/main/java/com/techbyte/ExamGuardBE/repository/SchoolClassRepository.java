package com.techbyte.ExamGuardBE.repository;

import com.techbyte.ExamGuardBE.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {
    @Query("""
            select c from SchoolClass c join c.teacher t
            where (:keyword = '' or lower(c.code) like :keyword or lower(c.name) like :keyword
                or lower(t.fullName) like :keyword or lower(t.username) like :keyword)
              and ((:includeTeaching = true and t.id = :userId)
                or exists (select s.id from SchoolClass enrolled join enrolled.students s
                    where enrolled.id = c.id and s.id = :userId))
            """)
    Page<SchoolClass> searchForUser(@Param("userId") Long userId, @Param("includeTeaching") boolean includeTeaching,
                                    @Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select c from SchoolClass c join c.teacher t
            where (:keyword = '' or lower(c.code) like :keyword or lower(c.name) like :keyword
                or lower(t.fullName) like :keyword or lower(t.username) like :keyword)
              and (:teacherId is null or t.id = :teacherId)
            """)
    Page<SchoolClass> search(@Param("keyword") String keyword, @Param("teacherId") Long teacherId,
                             Pageable pageable);

    @Query(value = """
            select s from User s
            where exists (select c.id from SchoolClass c join c.students member
                where c.id = :classId and member.id = s.id)
              and (:keyword = '' or lower(s.username) like :keyword
                or lower(s.email) like :keyword or lower(s.fullName) like :keyword)
            """,
            countQuery = """
            select count(s.id) from User s
            where exists (select c.id from SchoolClass c join c.students member
                where c.id = :classId and member.id = s.id)
              and (:keyword = '' or lower(s.username) like :keyword
                or lower(s.email) like :keyword or lower(s.fullName) like :keyword)
            """)
    Page<User> searchStudents(@Param("classId") Long classId, @Param("keyword") String keyword,
                              Pageable pageable);

    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
