package com.techbyte.ExamGuardBE;

import com.techbyte.ExamGuardBE.service.ExamPaperSnapshotService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.AopTestUtils;
import tools.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "EXAMGUARD_MYSQL_TEST", matches = "true")
class MySqlSessionConcurrencyTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean ExamPaperSnapshotService snapshots;
    long teacher, student, subject, schoolClass, exam;
    String prefix, token;

    @BeforeEach void prepareCommittedFixture() throws Exception {
        prefix = "t10c" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        transaction.executeWithoutResult(status -> {
            String hash = passwords.encode("Password@123");
            teacher = insert("insert into users(username,email,password_hash,full_name) values(?,?,?,?)", prefix + "teacher", prefix + "t@example.test", hash, prefix);
            student = insert("insert into users(username,email,password_hash,full_name) values(?,?,?,?)", prefix + "student", prefix + "s@example.test", hash, prefix);
            jdbc.update("insert into user_roles(user_id,role_id) select ?,id from roles where name='TEACHER'", teacher);
            jdbc.update("insert into user_roles(user_id,role_id) select ?,id from roles where name='STUDENT'", student);
            subject = insert("insert into subjects(code,name) values(?,?)", prefix, prefix);
            schoolClass = insert("insert into classes(code,name,teacher_id) values(?,?,?)", prefix, prefix, teacher);
            jdbc.update("insert into class_students(class_id,student_id) values(?,?)", schoolClass, student);
            exam = insert("insert into exams(subject_id,teacher_id,title,status,starts_at,ends_at,duration_minutes,max_attempts,shuffle_questions,shuffle_options) values(?,?,?,'PUBLISHED',UTC_TIMESTAMP()-interval 1 hour,UTC_TIMESTAMP()+interval 2 hour,30,3,true,true)", subject, teacher, prefix);
            jdbc.update("insert into exam_classes(exam_id,class_id) values(?,?)", exam, schoolClass);
            for (int index = 1; index <= 3; index++) {
                long q = insert("insert into questions(subject_id,created_by,content,type,difficulty) values(?,?,?,'SINGLE_CHOICE','EASY')", subject, teacher, prefix + index);
                jdbc.update("insert into question_options(question_id,content,is_correct,display_order) values(?, 'A',true,1),(?,'B',false,2)", q, q);
                jdbc.update("insert into exam_questions(exam_id,question_id,points,display_order) values(?,?,1,?)", exam, q, index);
            }
        });
        String body = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username", prefix + "student", "password", "Password@123"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); token = json.readTree(body).get("data").get("token").asText();
    }

    @AfterEach void cleanupOnlyCommittedFixture() {
        transaction.executeWithoutResult(status -> {
            jdbc.update("delete o from session_question_options o join session_questions q on q.id=o.session_question_id where q.exam_id=?", exam);
            jdbc.update("delete from session_questions where exam_id=?", exam); jdbc.update("delete from exam_sessions where exam_id=?", exam);
            jdbc.update("delete from exam_questions where exam_id=?", exam); jdbc.update("delete from exam_classes where exam_id=?", exam); jdbc.update("delete from exams where id=?", exam);
            jdbc.update("delete o from question_options o join questions q on q.id=o.question_id where q.created_by=?", teacher);
            jdbc.update("delete from questions where created_by=?", teacher); jdbc.update("delete from class_students where class_id=?", schoolClass);
            jdbc.update("delete from classes where id=?", schoolClass); jdbc.update("delete from subjects where id=?", subject);
            jdbc.update("delete from user_roles where user_id in (?,?)", student, teacher); jdbc.update("delete from users where id in (?,?)", student, teacher);
        });
    }

    @Test void simultaneousSameDeviceStartsCreateExactlyOneAttemptAndOnePaper() throws Exception {
        var results = simultaneous("concurrent-device-001", "concurrent-device-001");
        assertThat(results.stream().map(Result::status)).containsExactlyInAnyOrder(201, 200);
        assertThat(results.getFirst().id()).isEqualTo(results.getLast().id());
        assertThat(jdbc.queryForObject("select count(*) from exam_sessions where exam_id=?", Integer.class, exam)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from session_questions where exam_id=?", Integer.class, exam)).isEqualTo(3);
    }

    @Test void simultaneousDifferentDevicesAllowOneCreatorAndRejectTheOther() throws Exception {
        var results = simultaneous("concurrent-device-001", "concurrent-device-002");
        assertThat(results.stream().map(Result::status)).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("select count(*) from exam_sessions where exam_id=?", Integer.class, exam)).isEqualTo(1);
    }

    @Test void failureAfterSnapshotWriteRollsBackSessionAndAllCopiedRows() throws Exception {
        ExamPaperSnapshotService target = AopTestUtils.getUltimateTargetObject(snapshots);
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("Injected Task10 copy failure"); }).when(target).createOrGet(anyLong());
        assertThat(start("rollback-device-001").status()).isEqualTo(500);
        assertThat(jdbc.queryForObject("select count(*) from exam_sessions where exam_id=?", Integer.class, exam)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from session_questions where exam_id=?", Integer.class, exam)).isZero();
    }

    private List<Result> simultaneous(String first, String second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var a = pool.submit(() -> { gate.await(); return start(first); }); var b = pool.submit(() -> { gate.await(); return start(second); });
            gate.countDown(); return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }
    private Result start(String device) throws Exception {
        var result = mvc.perform(post("/api/student/exams/{id}/sessions", exam).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("deviceId", device)))).andReturn().getResponse();
        var data = json.readTree(result.getContentAsString()).get("data"); return new Result(result.getStatus(), data == null || data.isNull() ? null : data.get("id").asLong());
    }
    private long insert(String sql, Object... args) { jdbc.update(sql, args); return jdbc.queryForObject("select last_insert_id()", Long.class); }
    private record Result(int status, Long id) {}
}
