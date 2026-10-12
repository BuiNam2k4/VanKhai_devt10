package com.techbyte.ExamGuardBE;

import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.*;
import com.techbyte.ExamGuardBE.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class AbstractStudentExamIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    User teacher, student, other;
    Subject subject;
    SchoolClass assigned, secondClass, foreignClass;
    String token, otherToken, teacherToken, adminToken, prefix;

    @BeforeEach
    void prepare() throws Exception {
        prefix = "t09" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        for (RoleName name : RoleName.values()) if (roles.findByName(name).isEmpty()) { var role = new Role(); role.setName(name); roles.saveAndFlush(role); }
        String hash = passwords.encode("Password@123");
        teacher = account("teacher", RoleName.TEACHER, hash); student = account("student", RoleName.STUDENT, hash);
        other = account("other", RoleName.STUDENT, hash); var admin = account("admin", RoleName.ADMIN, hash);
        subject = subject("A");
        assigned = schoolClass("A", student); secondClass = schoolClass("B", student); foreignClass = schoolClass("C", other);
        em.flush(); token = login(student); otherToken = login(other); teacherToken = login(teacher); adminToken = login(admin);
    }

    @Test
    void membershipIsAppliedBeforeCountsAndPagingWithoutDuplicateExams() throws Exception {
        var alpha = exam("Alpha", ExamStatus.PUBLISHED, "OPEN", assigned); alpha.getClasses().add(secondClass);
        exam("Beta", ExamStatus.PUBLISHED, "OPEN", assigned); exam("Gamma", ExamStatus.PUBLISHED, "OPEN", assigned);
        var hidden = exam("Draft", ExamStatus.DRAFT, "OPEN", assigned); var foreign = exam("Foreign", ExamStatus.PUBLISHED, "OPEN", foreignClass); em.flush();
        mvc.perform(get("/api/student/exams").header("Authorization", bearer(token)).param("keyword", prefix).param("page", "2").param("size", "2").param("sortBy", "title:asc"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.pageNumber").value(2)).andExpect(jsonPath("$.data.pageSize").value(2))
            .andExpect(jsonPath("$.data.totalPages").value(2)).andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.exams.length()").value(1)).andExpect(jsonPath("$.data.exams[0].title").value(prefix + " Gamma"));
        mvc.perform(get("/api/student/exams").header("Authorization", bearer(token)).param("keyword", prefix).param("page", "0"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.pageNumber").value(0)).andExpect(jsonPath("$.data.totalElements").value(3));
        for (Exam inaccessible : List.of(hidden, foreign)) mvc.perform(get("/api/student/exams/{id}", inaccessible.getId()).header("Authorization", bearer(token))).andExpect(status().isNotFound());
        mvc.perform(get("/api/student/exams").header("Authorization", bearer(otherToken)).param("keyword", prefix))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void subjectStatusAndScheduleFiltersUseServerTime() throws Exception {
        var open = exam("Open", ExamStatus.PUBLISHED, "OPEN", assigned);
        exam("Future", ExamStatus.PUBLISHED, "UPCOMING", assigned); exam("Past", ExamStatus.PUBLISHED, "ENDED", assigned);
        exam("Closed", ExamStatus.CLOSED, "ENDED", assigned); em.flush();
        mvc.perform(get("/api/student/exams").header("Authorization", bearer(token)).param("subjectId", subject.getId().toString())
            .param("keyword", prefix).param("status", "PUBLISHED").param("window", "OPEN"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.exams[0].id").value(open.getId()))
            .andExpect(jsonPath("$.data.exams[0].participationStatus").value("AVAILABLE")).andExpect(jsonPath("$.data.exams[0].canStart").value(true));
        mvc.perform(get("/api/student/exams").header("Authorization", bearer(token)).param("keyword", prefix).param("window", "UPCOMING"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.exams[0].participationStatus").value("UPCOMING"));
        mvc.perform(get("/api/student/exams").header("Authorization", bearer(token)).param("keyword", prefix).param("window", "ENDED"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/student/exams").header("Authorization", bearer(token)).param("keyword", prefix).param("status", "DRAFT"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void attemptsAndActiveSessionsAreCountedOnlyForAuthenticatedStudent() throws Exception {
        var available = exam("Available", ExamStatus.PUBLISHED, "OPEN", assigned); available.setMaxAttempts(3);
        session(available, student, 1, SessionStatus.SUBMITTED, false); session(available, other, 1, SessionStatus.IN_PROGRESS, false);
        var exhausted = exam("Exhausted", ExamStatus.PUBLISHED, "OPEN", assigned); exhausted.setMaxAttempts(2);
        session(exhausted, student, 1, SessionStatus.SUBMITTED, false); session(exhausted, student, 2, SessionStatus.AUTO_SUBMITTED, false);
        var progress = exam("Progress", ExamStatus.PUBLISHED, "OPEN", assigned); var active = session(progress, student, 1, SessionStatus.IN_PROGRESS, false);
        var timeout = exam("Timeout", ExamStatus.PUBLISHED, "OPEN", assigned); timeout.setMaxAttempts(3); session(timeout, student, 1, SessionStatus.IN_PROGRESS, true); em.flush();
        mvc.perform(get("/api/student/exams/{id}", available.getId()).header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.attemptsUsed").value(1)).andExpect(jsonPath("$.data.attemptsRemaining").value(2))
            .andExpect(jsonPath("$.data.canStart").value(true)).andExpect(jsonPath("$.data.activeSessionId").isEmpty());
        mvc.perform(get("/api/student/exams/{id}", exhausted.getId()).header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.participationStatus").value("ATTEMPTS_EXHAUSTED")).andExpect(jsonPath("$.data.attemptsRemaining").value(0));
        mvc.perform(get("/api/student/exams/{id}", progress.getId()).header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.canStart").value(false)).andExpect(jsonPath("$.data.canResume").value(true))
            .andExpect(jsonPath("$.data.activeSessionId").value(active.getId()));
        mvc.perform(get("/api/student/exams/{id}", timeout.getId()).header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.participationStatus").value("SESSION_EXPIRED")).andExpect(jsonPath("$.data.canStart").value(false));
    }

    @Test
    void detailsDoNotExposePaperAnswersScoresOrOtherStudents() throws Exception {
        var exam = exam("Hidden results", ExamStatus.PUBLISHED, "OPEN", assigned); exam.setResultVisibility(ResultVisibility.HIDDEN);
        var q = new Question(); q.setSubject(subject); q.setCreatedBy(teacher); q.setContent("SECRET QUESTION"); q.setType(QuestionType.ESSAY); q.setDifficulty(Difficulty.EASY); q.setActive(true); em.persist(q);
        var link = new ExamQuestion(); link.setExam(exam); link.setQuestion(q); link.setPoints(BigDecimal.TEN); link.setDisplayOrder(1); em.persist(link);
        session(exam, student, 1, SessionStatus.SUBMITTED, false); em.flush();
        String body = mvc.perform(get("/api/student/exams/{id}", exam.getId()).header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.questionCount").value(1)).andExpect(jsonPath("$.data.resultVisibility").value("HIDDEN"))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("SECRET QUESTION", "correct", "options", "passwordHash", "score", "studentId", "deviceId", "classes");
        assertThat(json.readTree(body).get("data").get("startsAt").asText()).endsWith("Z");
        assertThat(json.readTree(body).get("data").get("serverTime").asText()).endsWith("Z");
    }

    @Test
    void studentSubjectCatalogHidesDraftOnlyAndForeignSubjectsAndPagesDistinctRows() throws Exception {
        exam("One", ExamStatus.PUBLISHED, "OPEN", assigned); exam("Same subject", ExamStatus.CLOSED, "ENDED", secondClass);
        var b = subject("B"); var visible = exam("Two", ExamStatus.PUBLISHED, "OPEN", assigned); visible.setSubject(b);
        var c = subject("C"); var hidden = exam("Draft", ExamStatus.DRAFT, "OPEN", assigned); hidden.setSubject(c);
        var d = subject("D"); var foreign = exam("Foreign", ExamStatus.PUBLISHED, "OPEN", foreignClass); foreign.setSubject(d); em.flush();
        mvc.perform(get("/api/student/subjects").header("Authorization", bearer(token)).param("page", "2").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.subjects[0].id").value(b.getId()));
    }

    @Test
    void databaseUtcValuesAreReadAndWrittenWithoutJvmTimezoneConversion() throws Exception {
        var item = exam("UTC mapping", ExamStatus.PUBLISHED, "UPCOMING", assigned);
        item.setStartsAt(LocalDateTime.of(2099, 5, 1, 2, 0)); item.setEndsAt(LocalDateTime.of(2099, 5, 1, 3, 0)); em.flush();
        String stored = jdbc.queryForObject("select starts_at from exams where id=?", String.class, item.getId());
        assertThat(stored).startsWith("2099-05-01 02:00:00");
        jdbc.update("update exams set starts_at=?, ends_at=? where id=?", "2099-06-01 02:00:00", "2099-06-01 03:00:00", item.getId());
        em.clear();
        mvc.perform(get("/api/student/exams/{id}", item.getId()).header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.startsAt").value("2099-06-01T02:00:00Z"))
            .andExpect(jsonPath("$.data.endsAt").value("2099-06-01T03:00:00Z"));
    }

    @Test
    void roleValidationAndRemovingClassMembershipProtectAllStudentEndpoints() throws Exception {
        var exam = exam("Own", ExamStatus.PUBLISHED, "OPEN", assigned); em.flush();
        mvc.perform(get("/api/student/exams")).andExpect(status().isUnauthorized());
        for (String roleToken : List.of(adminToken, teacherToken)) {
            mvc.perform(get("/api/student/exams").header("Authorization", bearer(roleToken))).andExpect(status().isForbidden());
            mvc.perform(get("/api/student/subjects").header("Authorization", bearer(roleToken))).andExpect(status().isForbidden());
        }
        assigned.getStudents().clear(); em.flush();
        mvc.perform(get("/api/student/exams/{id}", exam.getId()).header("Authorization", bearer(token))).andExpect(status().isNotFound());
        mvc.perform(get("/api/student/exams").header("Authorization", bearer(token)).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/student/exams").header("Authorization", bearer(token)).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/student/exams").header("Authorization", bearer(token)).param("sortBy", "teacher.passwordHash:asc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/student/exams").header("Authorization", bearer(token)).param("window", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/student/exams").header("Authorization", bearer(token)).param("subjectId", "0")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/student/exams").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isMethodNotAllowed());
    }

    private Exam exam(String title, ExamStatus status, String window, SchoolClass schoolClass) {
        var now = LocalDateTime.now(ZoneOffset.UTC); var item = new Exam(); item.setSubject(subject); item.setTeacher(teacher); item.setTitle(prefix + " " + title);
        item.setDurationMinutes(45); item.setMaxAttempts(2); item.setStatus(status);
        item.setStartsAt(window.equals("UPCOMING") ? now.plusDays(1) : window.equals("ENDED") ? now.minusDays(2) : now.minusHours(1));
        item.setEndsAt(window.equals("UPCOMING") ? now.plusDays(2) : window.equals("ENDED") ? now.minusDays(1) : now.plusHours(2));
        item.getClasses().add(schoolClass); em.persist(item); return item;
    }
    private ExamSession session(Exam exam, User user, int attempt, SessionStatus status, boolean expired) {
        var now = LocalDateTime.now(ZoneOffset.UTC); var session = new ExamSession(); session.setExam(exam); session.setStudent(user); session.setAttemptNumber(attempt); session.setStatus(status);
        session.setStartedAt(now.minusMinutes(30)); session.setExpiresAt(expired ? now.minusMinutes(1) : now.plusMinutes(15));
        if (status != SessionStatus.IN_PROGRESS) { session.setSubmittedAt(now.minusMinutes(5)); session.setScore(BigDecimal.TEN); }
        session.setMaxScore(BigDecimal.TEN); session.setDeviceId(prefix); em.persist(session); return session;
    }
    private Subject subject(String suffix) { var item = new Subject(); item.setCode(prefix + suffix); item.setName(prefix + suffix); em.persist(item); return item; }
    private SchoolClass schoolClass(String suffix, User member) {
        var item = new SchoolClass(); item.setCode(prefix + suffix); item.setName(prefix + suffix); item.setTeacher(teacher); item.getStudents().add(member); em.persist(item); return item;
    }
    private User account(String suffix, RoleName role, String hash) {
        var user = new User(); user.setUsername(prefix + suffix); user.setEmail(prefix + suffix + "@example.test"); user.setFullName(prefix + suffix);
        user.setPasswordHash(hash); user.setEnabled(true); user.getRoles().add(roles.findByName(role).orElseThrow()); return users.saveAndFlush(user);
    }
    private String login(User user) throws Exception {
        String body = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username", user.getUsername(), "password", "Password@123"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); return json.readTree(body).get("data").get("token").asText();
    }
    private static String bearer(String value) { return "Bearer " + value; }
}
