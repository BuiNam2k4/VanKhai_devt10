package com.techbyte.ExamGuardBE;

import com.techbyte.ExamGuardBE.entity.ExamSession;
import com.techbyte.ExamGuardBE.entity.StudentAnswer;
import com.techbyte.ExamGuardBE.enums.SessionStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;

/** Opt-in: creates schema in the configured MySQL database; fixture rows are rolled back. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "EXAMGUARD_MYSQL_TEST", matches = "true")
@Transactional
class MySqlSchemaTests {
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private long teacher, student, subject, exam, question, examQuestion, session, sessionQuestion, option, sessionOption;

    @BeforeEach
    void createAttempt() {
        teacher = insert("INSERT INTO users(username,email,password_hash,full_name) VALUES (?,?,?,?)", "task3_teacher", "task3_teacher@example.test", "test-only-hash", "Giảng viên");
        student = insert("INSERT INTO users(username,email,password_hash,full_name) VALUES (?,?,?,?)", "task3_student", "task3_student@example.test", "test-only-hash", "Phạm Văn Khải");
        subject = insert("INSERT INTO subjects(code,name) VALUES (?,?)", "TASK3", "Cơ sở dữ liệu");
        exam = newExam("Kiểm tra SQL");
        question = insert("INSERT INTO questions(subject_id,created_by,content,type,difficulty) VALUES (?,?,?,'SINGLE_CHOICE','EASY')", subject, teacher, "SQL là gì?");
        option = insert("INSERT INTO question_options(question_id,content,is_correct,display_order) VALUES (?,?,TRUE,1)", question, "Ngôn ngữ truy vấn");
        examQuestion = insert("INSERT INTO exam_questions(exam_id,question_id,points,display_order) VALUES (?,?,10,1)", exam, question);
        session = newSession(exam, 1);
        sessionQuestion = newSessionQuestion(session, examQuestion, exam, 1);
        sessionOption = insert("INSERT INTO session_question_options(session_question_id,source_option_id,content,is_correct,display_order) VALUES (?,?,?,TRUE,1)", sessionQuestion, option, "Ngôn ngữ truy vấn");
    }

    @Test
    void seedsExactlyThreeRoles() {
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+00:00");
        assertThat(jdbc.queryForList("SELECT name FROM roles ORDER BY name", String.class)).containsExactly("ADMIN", "STUDENT", "TEACHER");
    }

    @Test
    void loadsJpaMappingsAndKeepsQuestionSnapshotWhenBankChanges() {
        jdbc.update("UPDATE questions SET content='Nội dung mới' WHERE id=?", question);
        jdbc.update("UPDATE question_options SET content='Đáp án mới',is_correct=FALSE WHERE id=?", option);
        long answer = insert("INSERT INTO student_answers(session_question_id,selected_option_id,saved_at) VALUES (?,?,UTC_TIMESTAMP(6))", sessionQuestion, sessionOption);
        StudentAnswer loaded = entityManager.find(StudentAnswer.class, answer);
        assertThat(loaded.getSessionQuestion().getContent()).isEqualTo("SQL là gì?");
        assertThat(loaded.getSelectedOption().getContent()).isEqualTo("Ngôn ngữ truy vấn");
        assertThat(loaded.getSelectedOption().isCorrect()).isTrue();
        ExamSession attempt = entityManager.find(ExamSession.class, session);
        attempt.setStatus(SessionStatus.SUBMITTED);
        attempt.setSubmittedAt(attempt.getStartedAt().plusMinutes(1));
        attempt.setScore(new BigDecimal("10.00"));
        entityManager.flush();
        assertThat(attempt.getVersion()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateAccountsMembershipAndExamQuestions() {
        rejected(() -> jdbc.update("INSERT INTO users(username,email,password_hash,full_name) VALUES ('TASK3_STUDENT','other@example.test','hash','Other')"));
        long schoolClass = insert("INSERT INTO classes(code,name,teacher_id) VALUES ('TASK3','Lớp kiểm thử',?)", teacher);
        jdbc.update("INSERT INTO class_students(class_id,student_id) VALUES (?,?)", schoolClass, student);
        rejected(() -> jdbc.update("INSERT INTO class_students(class_id,student_id) VALUES (?,?)", schoolClass, student));
        rejected(() -> jdbc.update("INSERT INTO exam_questions(exam_id,question_id,points,display_order) VALUES (?,?,10,2)", exam, question));
    }

    @Test
    void rejectsOrphansInvalidScheduleAndNegativePoints() {
        rejected(() -> jdbc.update("INSERT INTO class_students(class_id,student_id) VALUES (-1,?)", student));
        rejected(() -> jdbc.update("UPDATE exams SET duration_minutes=0 WHERE id=?", exam));
        rejected(() -> jdbc.update("UPDATE exams SET status='PUBLISHED' WHERE id=?", exam));
        rejected(() -> jdbc.update("UPDATE exam_questions SET points=-1 WHERE id=?", examQuestion));
        rejected(() -> jdbc.update("UPDATE exam_sessions SET risk_score=-1 WHERE id=?", session));
    }

    @Test
    void permitsNewAttemptOnlyAfterCurrentAttemptIsSubmitted() {
        rejected(() -> newSession(exam, 2));
        jdbc.update("UPDATE exam_sessions SET status='SUBMITTED',submitted_at=UTC_TIMESTAMP(6) WHERE id=?", session);
        assertThat(newSession(exam, 2)).isPositive();
        rejected(() -> newSession(exam, 2));
    }

    @Test
    void rejectsQuestionFromAnotherExamInSession() {
        long otherExam = newExam("Đề khác");
        long otherQuestion = insert("INSERT INTO exam_questions(exam_id,question_id,points,display_order) VALUES (?,?,10,1)", otherExam, question);
        rejected(() -> newSessionQuestion(session, otherQuestion, otherExam, 2));
        rejected(() -> newSessionQuestion(session, otherQuestion, exam, 2));
    }

    @Test
    void rejectsAnswerOptionFromAnotherAttemptAndDuplicateAutosaveRows() {
        long otherExam = newExam("Đề khác");
        long otherExamQuestion = insert("INSERT INTO exam_questions(exam_id,question_id,points,display_order) VALUES (?,?,10,1)", otherExam, question);
        long otherSession = newSession(otherExam, 1);
        long otherSessionQuestion = newSessionQuestion(otherSession, otherExamQuestion, otherExam, 1);
        long otherOption = insert("INSERT INTO session_question_options(session_question_id,source_option_id,content,is_correct,display_order) VALUES (?,?,?,TRUE,1)", otherSessionQuestion, option, "Ngôn ngữ truy vấn");
        rejected(() -> insert("INSERT INTO student_answers(session_question_id,selected_option_id,saved_at) VALUES (?,?,UTC_TIMESTAMP(6))", sessionQuestion, otherOption));
        insert("INSERT INTO student_answers(session_question_id,selected_option_id,saved_at) VALUES (?,?,UTC_TIMESTAMP(6))", sessionQuestion, sessionOption);
        rejected(() -> insert("INSERT INTO student_answers(session_question_id,selected_option_id,saved_at) VALUES (?,?,UTC_TIMESTAMP(6))", sessionQuestion, sessionOption));
    }

    @Test
    void rejectsDuplicateViolationEventsAndDeletionOfHistory() {
        jdbc.update("INSERT INTO exam_violation_logs(session_id,type,occurred_at,event_id,risk_weight) VALUES (?,'TAB_SWITCH',UTC_TIMESTAMP(6),'task3-event',5)", session);
        rejected(() -> jdbc.update("INSERT INTO exam_violation_logs(session_id,type,occurred_at,event_id) VALUES (?,'TAB_SWITCH',UTC_TIMESTAMP(6),'task3-event')", session));
        rejected(() -> jdbc.update("DELETE FROM exam_sessions WHERE id=?", session));
        rejected(() -> jdbc.update("DELETE FROM users WHERE id=?", student));
    }

    private long newExam(String title) {
        return insert("INSERT INTO exams(subject_id,teacher_id,title,duration_minutes,max_attempts) VALUES (?,?,?,30,3)", subject, teacher, title);
    }

    private long newSession(long examId, int attempt) {
        return insert("INSERT INTO exam_sessions(exam_id,student_id,attempt_number,started_at,expires_at,max_score,device_id) VALUES (?,?,?,UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 30 MINUTE),10,'test-device')", examId, student, attempt);
    }

    private long newSessionQuestion(long sessionId, long examQuestionId, long examId, int order) {
        return insert("INSERT INTO session_questions(session_id,exam_question_id,exam_id,content,type,points,display_order) VALUES (?,?,?,'SQL là gì?','SINGLE_CHOICE',10,?)", sessionId, examQuestionId, examId, order);
    }

    private long insert(String sql, Object... args) {
        jdbc.update(sql, args);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void rejected(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(SQLException.class)
                .satisfies(error -> {
                    SQLException cause = (SQLException) ((DataAccessException) error).getMostSpecificCause();
                    // Duplicate key, referenced row, missing parent, or violated CHECK.
                    assertThat(cause.getErrorCode()).isIn(1062, 1451, 1452, 3819);
                });
    }
}
