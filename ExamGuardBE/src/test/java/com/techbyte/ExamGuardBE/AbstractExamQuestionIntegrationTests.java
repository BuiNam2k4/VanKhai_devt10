package com.techbyte.ExamGuardBE;

import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.*;
import com.techbyte.ExamGuardBE.repository.*;
import com.techbyte.ExamGuardBE.service.ExamPaperSnapshotService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class AbstractExamQuestionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired QuestionRepository questions;
    @Autowired ExamQuestionRepository links;
    @Autowired ExamSessionRepository sessions;
    @Autowired SessionQuestionRepository snapshots;
    @Autowired SessionQuestionOptionRepository snapshotOptions;
    @Autowired ExamPaperSnapshotService snapshotService;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager em;
    User teacher, other, student;
    Subject subject;
    Exam exam;
    Question first, second, essay;
    String teacherToken, otherToken, adminToken, studentToken, prefix;

    @BeforeEach
    void prepare() throws Exception {
        prefix = "t08" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        for (RoleName name : RoleName.values()) {
            if (roles.findByName(name).isEmpty()) { Role role = new Role(); role.setName(name); roles.saveAndFlush(role); }
        }
        String hash = passwords.encode("Password@123");
        teacher = account("teacher", RoleName.TEACHER, hash); other = account("other", RoleName.TEACHER, hash);
        student = account("student", RoleName.STUDENT, hash); User admin = account("admin", RoleName.ADMIN, hash);
        subject = new Subject(); subject.setCode(prefix); subject.setName(prefix); em.persist(subject);
        var schoolClass = new SchoolClass(); schoolClass.setCode(prefix); schoolClass.setName(prefix); schoolClass.setTeacher(teacher); em.persist(schoolClass);
        exam = new Exam(); exam.setSubject(subject); exam.setTeacher(teacher); exam.setTitle(prefix); exam.setDurationMinutes(60);
        exam.setStartsAt(LocalDateTime.of(2099, 5, 1, 0, 0)); exam.setEndsAt(exam.getStartsAt().plusHours(2)); exam.getClasses().add(schoolClass); em.persist(exam);
        first = question("First", QuestionType.SINGLE_CHOICE, teacher, subject);
        second = question("Second", QuestionType.SINGLE_CHOICE, teacher, subject);
        essay = question("Essay", QuestionType.ESSAY, teacher, subject); em.flush();
        teacherToken = login(teacher); otherToken = login(other); adminToken = login(admin); studentToken = login(student);
    }

    @Test
    void assignmentReorderAndRemovalPreserveLinkIdsAndBankRecords() throws Exception {
        JsonNode created = replace(teacherToken, payload(item(first, "2.50"), item(second, "1"), item(essay, "3.25")), 200);
        long firstLink = created.get("questions").get(0).get("id").asLong();
        long essayLink = created.get("questions").get(2).get("id").asLong();
        JsonNode reordered = replace(teacherToken, payload(item(essay, "4"), item(first, "2.50")), 200);
        assertThat(reordered.get("questions").get(0).get("id").asLong()).isEqualTo(essayLink);
        assertThat(reordered.get("questions").get(1).get("id").asLong()).isEqualTo(firstLink);
        assertThat(reordered.get("totalPoints").decimalValue()).isEqualByComparingTo("6.5");
        assertThat(reordered.get("questions").get(1).get("displayOrder").asInt()).isEqualTo(2);
        assertThat(questions.existsById(second.getId())).isTrue();
        mvc.perform(get("/api/teacher/exams/{id}", exam.getId()).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.questionCount").value(2));
        replace(teacherToken, payload(), 200);
        assertThat(links.existsByExamId(exam.getId())).isFalse(); assertThat(questions.existsById(first.getId())).isTrue();
    }

    @Test
    void invalidSelectionIsRejectedBeforeAnyExistingLinksChange() throws Exception {
        replace(teacherToken, payload(item(first, "1")), 200);
        replace(teacherToken, payload(item(first, "1"), item(first, "2")), 400);
        var otherSubject = new Subject(); otherSubject.setCode(prefix + "B"); otherSubject.setName("Other"); em.persist(otherSubject);
        var wrongSubject = question("Wrong subject", QuestionType.ESSAY, teacher, otherSubject);
        replace(teacherToken, payload(item(second, "1"), item(wrongSubject, "1")), 400);
        second.setActive(false); em.flush(); replace(teacherToken, payload(item(second, "1")), 400);
        var foreign = question("Foreign", QuestionType.ESSAY, other, subject);
        replace(teacherToken, payload(item(foreign, "1")), 403);
        replace(teacherToken, payload(Map.of("questionId", Long.MAX_VALUE, "points", 1)), 404);
        var current = links.findByExamIdOrderByDisplayOrderAsc(exam.getId());
        assertThat(current).hasSize(1); assertThat(current.getFirst().getQuestion().getId()).isEqualTo(first.getId());
        assertThat(current.getFirst().getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void pointsPrecisionAndTotalMatchDatabaseBounds() throws Exception {
        for (String invalid : List.of("0", "-1", "0.001", "1000000", "1.001")) replace(teacherToken, payload(item(first, invalid)), 400);
        replace(teacherToken, payload(item(first, "999999.99"), item(second, "0.01")), 400);
        mvc.perform(put(url()).header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(url()).header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON).content("{\"questions\":[null]}"))
                .andExpect(status().isBadRequest());
        replace(teacherToken, payload(item(first, "0.01")), 200);
        assertThat(links.findByExamIdOrderByDisplayOrderAsc(exam.getId()).getFirst().getPoints()).isEqualByComparingTo("0.01");
    }

    @Test
    void examOwnershipAndManagementRolesProtectAssignmentAndPreview() throws Exception {
        replace(teacherToken, payload(item(first, "1")), 200);
        mvc.perform(get(url())).andExpect(status().isUnauthorized());
        for (String token : List.of(otherToken, studentToken)) {
            mvc.perform(get(url()).header("Authorization", bearer(token))).andExpect(status().isForbidden());
            replace(token, payload(), 403);
            mvc.perform(post(url() + "/preview").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        }
        var foreign = question("Admin selection", QuestionType.ESSAY, other, subject);
        replace(adminToken, payload(item(foreign, "2")), 200);
        // An existing admin-assigned question can be retained and reordered by the exam owner.
        replace(teacherToken, payload(item(foreign, "3"), item(first, "1")), 200);
        mvc.perform(get("/api/admin/exams/{id}/questions", exam.getId()).header("Authorization", bearer(teacherToken))).andExpect(status().isForbidden());
    }

    @Test
    void previewOmitsGradingKeysAndDoesNotChangeSourceOrCreateSessions() throws Exception {
        replace(teacherToken, payload(item(first, "2"), item(essay, "3")), 200);
        String body = mvc.perform(post(url() + "/preview").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalPoints").value(5))
                .andExpect(jsonPath("$.data.questions[0].content").value(first.getContent()))
                .andExpect(jsonPath("$.data.questions[0].options[0].content").value("First A"))
                .andExpect(jsonPath("$.data.questions[1].options.length()").value(0)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("correct", "sourceOption", "passwordHash");
        exam.setShuffleQuestions(true); exam.setShuffleOptions(true); em.flush();
        mvc.perform(post(url() + "/preview").header("Authorization", bearer(teacherToken))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(2));
        assertThat(links.findByExamIdOrderByDisplayOrderAsc(exam.getId()).stream().map(link -> link.getQuestion().getId())).containsExactly(first.getId(), essay.getId());
        assertThat(sessions.findAll().stream().filter(session -> session.getExam().getId().equals(exam.getId()))).isEmpty();
    }

    @Test
    void publishedAndClosedPapersCannotBeChanged() throws Exception {
        replace(teacherToken, payload(item(first, "1"), item(essay, "2")), 200);
        mvc.perform(post("/api/teacher/exams/{id}/publish", exam.getId()).header("Authorization", bearer(teacherToken))).andExpect(status().isOk());
        replace(teacherToken, payload(), 409); replace(adminToken, payload(), 409);
        mvc.perform(get(url()).header("Authorization", bearer(teacherToken))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        mvc.perform(post("/api/teacher/exams/{id}/close", exam.getId()).header("Authorization", bearer(teacherToken))).andExpect(status().isOk());
        replace(teacherToken, payload(item(second, "1")), 409);
    }

    @Test
    void snapshotsArePersistedOnceAndKeepSourceAnswerIdentityAfterBankEdits() throws Exception {
        replace(teacherToken, payload(item(first, "2.50"), item(second, "3"), item(essay, "4")), 200);
        exam.setShuffleQuestions(true); exam.setShuffleOptions(true); exam.setStatus(ExamStatus.PUBLISHED); em.flush();
        ExamSession session = session();
        var created = snapshotService.createOrGet(session.getId());
        assertThat(created).hasSize(3); assertThat(session.getMaxScore()).isEqualByComparingTo("9.50");
        List<Long> questionIds = created.stream().map(SessionQuestion::getId).toList();
        Map<Long, List<Long>> optionIds = new HashMap<>();
        for (int index = 0; index < created.size(); index++) {
            var question = created.get(index); assertThat(question.getDisplayOrder()).isEqualTo(index + 1);
            var options = snapshotOptions.findBySessionQuestionIdOrderByDisplayOrderAsc(question.getId());
            optionIds.put(question.getId(), options.stream().map(SessionQuestionOption::getId).toList());
            for (int order = 0; order < options.size(); order++) {
                var option = options.get(order); assertThat(option.getDisplayOrder()).isEqualTo(order + 1);
                assertThat(option.isCorrect()).isEqualTo(option.getSourceOption().isCorrect());
                assertThat(option.getContent()).isEqualTo(option.getSourceOption().getContent());
            }
        }
        first.setContent("Updated bank content"); first.setActive(false);
        var option = em.createQuery("select o from QuestionOption o where o.question.id = :id order by o.displayOrder", QuestionOption.class)
                .setParameter("id", first.getId()).getResultList().getFirst(); option.setContent("Updated bank choice"); em.flush(); em.clear();
        var resumed = snapshotService.createOrGet(session.getId());
        assertThat(resumed.stream().map(SessionQuestion::getId)).containsExactlyElementsOf(questionIds);
        assertThat(resumed.stream().map(SessionQuestion::getContent)).doesNotContain("Updated bank content");
        for (var question : resumed) {
            var options = snapshotOptions.findBySessionQuestionIdOrderByDisplayOrderAsc(question.getId());
            assertThat(options.stream().map(SessionQuestionOption::getId)).containsExactlyElementsOf(optionIds.get(question.getId()));
            assertThat(options.stream().map(SessionQuestionOption::getContent)).doesNotContain("Updated bank choice");
        }
    }

    @Test
    void invalidSnapshotWritesNothingAndHistoricalDraftCannotChangeQuestions() throws Exception {
        replace(teacherToken, payload(item(first, "1")), 200);
        ExamSession session = session();
        assertThatThrownBy(() -> snapshotService.createOrGet(session.getId())).isInstanceOf(com.techbyte.ExamGuardBE.exception.AppException.class);
        assertThat(snapshots.findBySessionIdOrderByDisplayOrderAsc(session.getId())).isEmpty();
        replace(teacherToken, payload(), 409);
        exam.setStatus(ExamStatus.PUBLISHED); first.setActive(false); em.flush();
        assertThatThrownBy(() -> snapshotService.createOrGet(session.getId())).isInstanceOf(com.techbyte.ExamGuardBE.exception.AppException.class);
        assertThat(snapshots.findBySessionIdOrderByDisplayOrderAsc(session.getId())).isEmpty();
    }

    private String url() { return "/api/teacher/exams/" + exam.getId() + "/questions"; }
    private Map<String, Object> item(Question question, String points) { return Map.of("questionId", question.getId(), "points", new BigDecimal(points)); }
    @SafeVarargs private final Map<String, Object> payload(Map<String, Object>... items) { return Map.of("questions", List.of(items)); }
    private JsonNode replace(String token, Map<String, Object> payload, int expected) throws Exception {
        String body = mvc.perform(put(url()).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(); return json.readTree(body).get("data");
    }
    private User account(String suffix, RoleName roleName, String hash) {
        User user = new User(); user.setUsername(prefix + suffix); user.setEmail(prefix + suffix + "@example.test"); user.setFullName(prefix + suffix);
        user.setPasswordHash(hash); user.setEnabled(true); user.getRoles().add(roles.findByName(roleName).orElseThrow()); return users.saveAndFlush(user);
    }
    private String login(User user) throws Exception {
        String body = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", user.getUsername(), "password", "Password@123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); return json.readTree(body).get("data").get("token").asText();
    }
    private Question question(String content, QuestionType type, User author, Subject subject) {
        var question = new Question(); question.setSubject(subject); question.setCreatedBy(author); question.setContent(prefix + " " + content);
        question.setType(type); question.setDifficulty(Difficulty.EASY); question.setActive(true); em.persist(question);
        if (type == QuestionType.SINGLE_CHOICE) for (int index = 1; index <= 2; index++) {
            var option = new QuestionOption(); option.setQuestion(question); option.setContent(content + (index == 1 ? " A" : " B"));
            option.setCorrect(index == 1); option.setDisplayOrder(index); em.persist(option);
        }
        em.flush(); return question;
    }
    private ExamSession session() {
        var session = new ExamSession(); session.setExam(exam); session.setStudent(student); session.setAttemptNumber(1);
        session.setStartedAt(LocalDateTime.now(ZoneOffset.UTC)); session.setExpiresAt(session.getStartedAt().plusHours(1));
        session.setMaxScore(BigDecimal.ONE); session.setDeviceId(prefix); return sessions.saveAndFlush(session);
    }
    private static String bearer(String token) { return "Bearer " + token; }
}
