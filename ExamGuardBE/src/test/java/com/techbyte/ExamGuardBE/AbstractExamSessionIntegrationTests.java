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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class AbstractExamSessionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ExamSessionRepository sessions;
    @Autowired SessionQuestionRepository snapshots;
    @Autowired SessionQuestionOptionRepository snapshotOptions;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager em;
    @MockitoBean Clock clock;
    final Instant fixed = Instant.parse("2030-01-01T02:00:00Z");
    User teacher, student, other;
    Exam exam;
    SchoolClass schoolClass;
    Question choice, essay;
    String token, otherToken, teacherToken, adminToken, prefix;
    final String device = "test-device-001";

    @BeforeEach void prepare() throws Exception {
        when(clock.instant()).thenReturn(fixed);
        prefix = "t10" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        for (RoleName name : RoleName.values()) if (roles.findByName(name).isEmpty()) { var role = new Role(); role.setName(name); roles.saveAndFlush(role); }
        String hash = passwords.encode("Password@123"); teacher = account("teacher", RoleName.TEACHER, hash);
        student = account("student", RoleName.STUDENT, hash); other = account("other", RoleName.STUDENT, hash); var admin = account("admin", RoleName.ADMIN, hash);
        var subject = new Subject(); subject.setCode(prefix); subject.setName(prefix); em.persist(subject);
        schoolClass = new SchoolClass(); schoolClass.setCode(prefix); schoolClass.setName(prefix); schoolClass.setTeacher(teacher); schoolClass.getStudents().add(student); em.persist(schoolClass);
        exam = new Exam(); exam.setSubject(subject); exam.setTeacher(teacher); exam.setTitle(prefix); exam.setStatus(ExamStatus.PUBLISHED);
        exam.setStartsAt(now().minusHours(1)); exam.setEndsAt(now().plusHours(2)); exam.setDurationMinutes(45); exam.setMaxAttempts(2);
        exam.setShuffleQuestions(true); exam.setShuffleOptions(true); exam.getClasses().add(schoolClass); em.persist(exam);
        choice = question(QuestionType.SINGLE_CHOICE, 1, "2.50"); essay = question(QuestionType.ESSAY, 2, "3.25"); em.flush();
        token = login(student); otherToken = login(other); teacherToken = login(teacher); adminToken = login(admin);
    }

    @Test void creationCopiesPaperAndUsesServerDeadlineAndScoresWithoutGradingLeaks() throws Exception {
        var created = start(token, device, 201);
        assertThat(created.get("startedAt").asText()).isEqualTo("2030-01-01T02:00:00Z");
        assertThat(created.get("expiresAt").asText()).isEqualTo("2030-01-01T02:45:00Z");
        assertThat(created.get("remainingSeconds").asLong()).isEqualTo(2700);
        assertThat(created.get("maxScore").decimalValue()).isEqualByComparingTo("5.75");
        assertThat(created.get("questions").size()).isEqualTo(2);
        assertThat(created.toString()).doesNotContain("correct", "sourceOption", "examQuestion", "deviceId", "passwordHash", "awardedPoints", "riskScore");
        var session = sessions.findById(created.get("id").asLong()).orElseThrow();
        assertThat(session.getStudent().getId()).isEqualTo(student.getId()); assertThat(session.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(session.getUserAgent()).isEqualTo("Task10 integration");
        var saved = snapshots.findBySessionIdOrderByDisplayOrderAsc(session.getId());
        for (var q : saved) {
            var options = snapshotOptions.findBySessionQuestionIdOrderByDisplayOrderAsc(q.getId());
            if (q.getType() == QuestionType.SINGLE_CHOICE) assertThat(options.stream().filter(SessionQuestionOption::isCorrect)).hasSize(1);
            else assertThat(options).isEmpty();
        }
    }

    @Test void examClosingCapsPersonalDuration() throws Exception {
        exam.setEndsAt(now().plusMinutes(10)); em.flush();
        var created = start(token, device, 201);
        assertThat(created.get("expiresAt").asText()).isEqualTo("2030-01-01T02:10:00Z");
        assertThat(created.get("remainingSeconds").asLong()).isEqualTo(600);
    }

    @Test void repeatedStartAndRestoreKeepIdsOrderAndDeadlineAfterBankEdits() throws Exception {
        var created = start(token, device, 201); long id = created.get("id").asLong();
        choice.setContent("Changed bank text"); choice.setActive(false); em.flush();
        var option = em.createQuery("select o from QuestionOption o where o.question.id=:id order by o.displayOrder", QuestionOption.class)
            .setParameter("id", choice.getId()).getResultList().getFirst(); option.setContent("Changed bank option"); em.flush(); em.clear();
        when(clock.instant()).thenReturn(fixed.plusSeconds(60));
        var repeated = start(token, device, 200); var restored = restore(token, id, device, 200);
        assertThat(repeated.get("id").asLong()).isEqualTo(id);
        assertThat(repeated.get("expiresAt")).isEqualTo(created.get("expiresAt"));
        assertThat(repeated.get("questions")).isEqualTo(created.get("questions"));
        assertThat(restored.get("questions")).isEqualTo(created.get("questions"));
        assertThat(restored.get("remainingSeconds").asLong()).isEqualTo(2640);
        assertThat(sessions.findByExamIdAndStudentIdOrderByAttemptNumberAsc(exam.getId(), student.getId())).hasSize(1);
    }

    @Test void expiredAndClosedLiveSessionsNeverGetFreshTimeOrAnotherAttempt() throws Exception {
        var created = start(token, device, 201); long id = created.get("id").asLong();
        exam.setStatus(ExamStatus.CLOSED); em.flush();
        assertThat(restore(token, id, device, 200).get("canContinue").asBoolean()).isTrue();
        when(clock.instant()).thenReturn(fixed.plusSeconds(2700));
        var expired = restore(token, id, device, 200);
        assertThat(expired.get("expired").asBoolean()).isTrue(); assertThat(expired.get("canContinue").asBoolean()).isFalse();
        assertThat(expired.get("remainingSeconds").asLong()).isZero();
        var repeated = start(token, device, 200); assertThat(repeated.get("id").asLong()).isEqualTo(id);
        assertThat(repeated.get("expiresAt")).isEqualTo(created.get("expiresAt"));
    }

    @Test void ownershipMembershipRolesAndDeviceAreCheckedOnEveryRequest() throws Exception {
        start(otherToken, device, 404);
        mvc.perform(post(startUrl()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("deviceId", device)))).andExpect(status().isUnauthorized());
        start(teacherToken, device, 403); start(adminToken, device, 403);
        var created = start(token, device, 201); long id = created.get("id").asLong();
        start(token, "another-device-002", 409); restore(token, id, "another-device-002", 409); restore(otherToken, id, device, 404); restore(adminToken, id, device, 403);
        schoolClass.getStudents().clear(); em.flush(); start(token, device, 404); restore(token, id, device, 404);
        assertThat(sessions.findByExamIdAndStudentIdOrderByAttemptNumberAsc(exam.getId(), student.getId())).hasSize(1);
    }

    @Test void unavailableScheduleAndDraftOrClosedExamsRejectAllocation() throws Exception {
        exam.setStatus(ExamStatus.DRAFT); em.flush(); start(token, device, 404);
        exam.setStatus(ExamStatus.CLOSED); em.flush(); start(token, device, 409);
        exam.setStatus(ExamStatus.PUBLISHED); exam.setStartsAt(now().plusSeconds(1)); em.flush(); start(token, device, 409);
        exam.setStartsAt(now().minusHours(1)); exam.setEndsAt(now()); em.flush(); start(token, device, 409);
        assertThat(sessions.findByExamIdAndStudentIdOrderByAttemptNumberAsc(exam.getId(), student.getId())).isEmpty();
    }

    @Test void laterAttemptNumbersAndMaximumAttemptsAreEnforced() throws Exception {
        var first = start(token, device, 201); complete(first.get("id").asLong());
        var second = start(token, device, 201); assertThat(second.get("attemptNumber").asInt()).isEqualTo(2); complete(second.get("id").asLong());
        start(token, device, 409);
        assertThat(sessions.findByExamIdAndStudentIdOrderByAttemptNumberAsc(exam.getId(), student.getId())).hasSize(2);
    }

    @Test void malformedDevicesAreRejectedAndClientCannotChooseDeadlineOrStudent() throws Exception {
        for (String bad : List.of("", "short", "contains spaces", "x".repeat(101))) start(token, bad, 400);
        String body = mvc.perform(post(startUrl()).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("deviceId", device, "studentId", other.getId(), "startedAt", "2000-01-01T00:00:00Z", "expiresAt", "2099-01-01T00:00:00Z", "durationMinutes", 999))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var data = json.readTree(body).get("data"); assertThat(data.get("expiresAt").asText()).isEqualTo("2030-01-01T02:45:00Z");
        assertThat(sessions.findById(data.get("id").asLong()).orElseThrow().getStudent().getId()).isEqualTo(student.getId());
        mvc.perform(get("/api/student/sessions/{id}", data.get("id").asLong()).header("Authorization", bearer(token)))
            .andExpect(status().isBadRequest());
    }

    @Test void invalidPaperCreatesNoSessionAndMissingSnapshotIsNotRegenerated() throws Exception {
        choice.setActive(false); em.flush(); start(token, device, 400);
        assertThat(sessions.findByExamIdAndStudentIdOrderByAttemptNumberAsc(exam.getId(), student.getId())).isEmpty();
        var broken = new ExamSession(); broken.setExam(exam); broken.setStudent(student); broken.setAttemptNumber(1); broken.setStartedAt(now());
        broken.setExpiresAt(now().plusMinutes(45)); broken.setMaxScore(BigDecimal.ONE); broken.setDeviceId(device); sessions.saveAndFlush(broken);
        restore(token, broken.getId(), device, 409); start(token, device, 409);
        assertThat(snapshots.findBySessionIdOrderByDisplayOrderAsc(broken.getId())).isEmpty();
    }

    private void complete(long id) { var session = sessions.findById(id).orElseThrow(); session.setStatus(SessionStatus.SUBMITTED); session.setSubmittedAt(now()); sessions.flush(); }
    private String startUrl() { return "/api/student/exams/" + exam.getId() + "/sessions"; }
    private JsonNode start(String auth, String deviceId, int expected) throws Exception {
        String body = mvc.perform(post(startUrl()).header("Authorization", bearer(auth)).header("User-Agent", "Task10 integration")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("deviceId", deviceId))))
            .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(); return json.readTree(body).get("data");
    }
    private JsonNode restore(String auth, long id, String deviceId, int expected) throws Exception {
        String body = mvc.perform(get("/api/student/sessions/{id}", id).param("deviceId", deviceId).header("Authorization", bearer(auth)))
            .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(); return json.readTree(body).get("data");
    }
    private Question question(QuestionType type, int order, String points) {
        var q = new Question(); q.setSubject(exam.getSubject()); q.setCreatedBy(teacher); q.setContent(prefix + type); q.setType(type); q.setDifficulty(Difficulty.EASY); q.setActive(true); em.persist(q);
        if (type == QuestionType.SINGLE_CHOICE) for (int index = 1; index <= 4; index++) { var option = new QuestionOption(); option.setQuestion(q); option.setContent(prefix + " choice " + index); option.setCorrect(index == 2); option.setDisplayOrder(index); em.persist(option); }
        var link = new ExamQuestion(); link.setExam(exam); link.setQuestion(q); link.setPoints(new BigDecimal(points)); link.setDisplayOrder(order); em.persist(link); return q;
    }
    private User account(String suffix, RoleName role, String hash) {
        var user = new User(); user.setUsername(prefix + suffix); user.setEmail(prefix + suffix + "@example.test"); user.setFullName(prefix + suffix);
        user.setPasswordHash(hash); user.setEnabled(true); user.getRoles().add(roles.findByName(role).orElseThrow()); return users.saveAndFlush(user);
    }
    private String login(User user) throws Exception {
        String body = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username", user.getUsername(), "password", "Password@123"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); return json.readTree(body).get("data").get("token").asText();
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(fixed, ZoneOffset.UTC); }
    private static String bearer(String token) { return "Bearer " + token; }
}
