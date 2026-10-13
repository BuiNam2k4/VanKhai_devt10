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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class AbstractExamManagementIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired SubjectRepository subjects;
    @Autowired SchoolClassRepository classes;
    @Autowired ExamRepository exams;
    @Autowired ExamQuestionRepository links;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager em;
    User teacher, other, student;
    Subject subject;
    SchoolClass schoolClass, otherClass;
    String teacherToken, otherToken, adminToken, studentToken, prefix;

    @BeforeEach
    void prepare() throws Exception {
        prefix = "t07" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        for (RoleName name : RoleName.values()) {
            if (roles.findByName(name).isEmpty()) { Role role = new Role(); role.setName(name); roles.saveAndFlush(role); }
        }
        String hash = passwords.encode("Password@123");
        teacher = account("teacher", RoleName.TEACHER, hash);
        other = account("other", RoleName.TEACHER, hash);
        student = account("student", RoleName.STUDENT, hash);
        User admin = account("admin", RoleName.ADMIN, hash);
        subject = new Subject(); subject.setCode(prefix); subject.setName(prefix); subjects.saveAndFlush(subject);
        schoolClass = schoolClass("A", teacher); otherClass = schoolClass("B", other);
        teacherToken = login(teacher); otherToken = login(other); studentToken = login(student); adminToken = login(admin);
    }

    @Test
    void draftCrudPersistsScheduleAsUtcAndConfiguration() throws Exception {
        var payload = request("CRUD");
        payload.put("startsAt", "2099-05-01T09:00:00+07:00"); payload.put("endsAt", "2099-05-01T10:30:00+07:00");
        payload.put("shuffleQuestions", true); payload.put("shuffleOptions", true);
        var created = create(teacherToken, payload);
        long id = created.get("id").asLong();
        assertThat(created.get("startsAt").asText()).isEqualTo("2099-05-01T02:00:00Z");
        assertThat(created.get("endsAt").asText()).isEqualTo("2099-05-01T03:30:00Z");
        assertThat(created.get("teacherId").asLong()).isEqualTo(teacher.getId());
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("shuffleQuestions").asBoolean()).isTrue();
        assertThat(created.get("classes").get(0).get("id").asLong()).isEqualTo(schoolClass.getId());
        for (String policy : List.of("HIDDEN", "AFTER_SUBMISSION", "AFTER_EXAM_END")) {
            payload.put("resultVisibility", policy); payload.put("durationMinutes", 90); payload.put("maxAttempts", 3);
            mvc.perform(put("/api/teacher/exams/{id}", id).header("Authorization", bearer(teacherToken))
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.resultVisibility").value(policy))
                    .andExpect(jsonPath("$.data.durationMinutes").value(90)).andExpect(jsonPath("$.data.maxAttempts").value(3));
        }
        payload.put("startsAt", null); payload.put("endsAt", null); payload.put("classIds", List.of());
        mvc.perform(put("/api/teacher/exams/{id}", id).header("Authorization", bearer(teacherToken))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.startsAt").isEmpty()).andExpect(jsonPath("$.data.classes.length()").value(0));
        mvc.perform(delete("/api/teacher/exams/{id}", id).header("Authorization", bearer(teacherToken))).andExpect(status().isOk());
        mvc.perform(get("/api/teacher/exams/{id}", id).header("Authorization", bearer(teacherToken))).andExpect(status().isNotFound());
    }

    @Test
    void filteringAndPaginationKeepExistingContractAndTeacherScope() throws Exception {
        for (String name : List.of("Alpha", "Beta", "Gamma")) create(teacherToken, request(name));
        var foreign = request("Other"); foreign.put("classIds", List.of(otherClass.getId())); create(otherToken, foreign);
        mvc.perform(get("/api/teacher/exams").header("Authorization", bearer(teacherToken)).param("keyword", prefix)
                .param("subjectId", subject.getId().toString()).param("status", "DRAFT").param("teacherId", other.getId().toString())
                .param("sortBy", "title:asc").param("page", "2").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pageNumber").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(2)).andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3)).andExpect(jsonPath("$.data.exams.length()").value(1))
                .andExpect(jsonPath("$.data.exams[0].title").value(prefix + " Gamma"))
                .andExpect(jsonPath("$.data.exams[0].classes.length()").value(1));
        mvc.perform(get("/api/admin/exams").header("Authorization", bearer(adminToken)).param("keyword", prefix).param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pageNumber").value(0)).andExpect(jsonPath("$.data.totalElements").value(4));
        mvc.perform(get("/api/admin/exams").header("Authorization", bearer(adminToken)).param("keyword", prefix).param("teacherId", other.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        for (String invalidSort : List.of("teacher.passwordHash:asc", "id:invalid")) {
            mvc.perform(get("/api/teacher/exams").header("Authorization", bearer(teacherToken)).param("sortBy", invalidSort)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/teacher/exams").header("Authorization", bearer(teacherToken)).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/teacher/exams").header("Authorization", bearer(teacherToken)).param("status", "UNKNOWN")).andExpect(status().isBadRequest());
    }

    @Test
    void ownershipRolesAndTeacherSpoofingAreEnforced() throws Exception {
        long id = create(teacherToken, request("Private")).get("id").asLong();
        mvc.perform(get("/api/teacher/exams")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/teacher/exams").header("Authorization", bearer(studentToken))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/exams").header("Authorization", bearer(teacherToken))).andExpect(status().isForbidden());
        mvc.perform(get("/api/teacher/exams/{id}", id).header("Authorization", bearer(otherToken))).andExpect(status().isForbidden());
        mvc.perform(put("/api/teacher/exams/{id}", id).header("Authorization", bearer(otherToken))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("Steal")))).andExpect(status().isForbidden());
        for (String action : List.of("publish", "close")) mvc.perform(post("/api/teacher/exams/{id}/" + action, id)
                .header("Authorization", bearer(otherToken))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/teacher/exams/{id}", id).header("Authorization", bearer(otherToken))).andExpect(status().isForbidden());
        var spoof = request("Spoof"); spoof.put("teacherId", other.getId()); postInvalid(teacherToken, spoof, 403);
        mvc.perform(get("/api/admin/exams/{id}", id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.teacher.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.questions").doesNotExist());
        mvc.perform(delete("/api/admin/exams/{id}", id).header("Authorization", bearer(adminToken))).andExpect(status().isOk());
    }

    @Test
    void payloadAndScheduleValidationRejectInvalidData() throws Exception {
        for (Map.Entry<String, Object> entry : Map.<String, Object>of("title", " ", "durationMinutes", 0,
                "maxAttempts", -1, "subjectId", 0, "resultVisibility", "INVALID", "classIds", List.of(-1)).entrySet()) {
            var payload = request("Invalid"); payload.put(entry.getKey(), entry.getValue()); postInvalid(teacherToken, payload, 400);
        }
        var payload = request("Schedule"); payload.put("startsAt", "2099-05-01T09:00:00Z"); postInvalid(teacherToken, payload, 400);
        payload.put("endsAt", "2099-05-01T16:00:00+07:00"); postInvalid(teacherToken, payload, 400);
        payload.put("endsAt", "2099-05-01T08:59:00Z"); postInvalid(teacherToken, payload, 400);
        payload = request("Invalid zone"); payload.put("startsAt", "2099-05-01T09:00:00"); postInvalid(teacherToken, payload, 400);
        payload = request("Unknown subject"); payload.put("subjectId", Long.MAX_VALUE); postInvalid(teacherToken, payload, 404);
        payload = request("Unknown class"); payload.put("classIds", List.of(Long.MAX_VALUE)); postInvalid(teacherToken, payload, 404);
    }

    @Test
    void classAssignmentAndAdminTeacherSelectionMustBeValid() throws Exception {
        var payload = request("Wrong class"); payload.put("classIds", List.of(otherClass.getId())); postInvalid(teacherToken, payload, 400);
        payload = request("Admin requires teacher"); postInvalid(adminToken, payload, 400);
        payload.put("teacherId", student.getId()); postInvalid(adminToken, payload, 400);
        payload.put("teacherId", teacher.getId()); var created = create(adminToken, payload);
        assertThat(created.get("teacherId").asLong()).isEqualTo(teacher.getId());
        payload.put("teacherId", other.getId()); postInvalid(adminToken, payload, 400);
        other.setEnabled(false); users.saveAndFlush(other); payload.put("classIds", List.of(otherClass.getId())); postInvalid(adminToken, payload, 400);
        mvc.perform(get("/api/teacher/classes").header("Authorization", bearer(teacherToken)).param("keyword", prefix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.classes[0].id").value(schoolClass.getId()));
        mvc.perform(get("/api/teacher/classes").header("Authorization", bearer(studentToken))).andExpect(status().isForbidden());
    }

    @Test
    void publicationRequiresScheduleClassAndValidQuestions() throws Exception {
        var payload = request("Ready"); long id = create(teacherToken, payload).get("id").asLong();
        publish(id, 400);
        payload.put("startsAt", "2099-05-01T09:00:00Z"); payload.put("endsAt", "2099-05-01T10:00:00Z");
        mvc.perform(put("/api/teacher/exams/{id}", id).header("Authorization", bearer(teacherToken))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isOk());
        publish(id, 400);
        Question question = attachQuestion(id, QuestionType.SINGLE_CHOICE);
        publish(id, 400); // Missing answer choices.
        choice(question, "A", true, 1); QuestionOption second = choice(question, "B", true, 2);
        publish(id, 400); // Two correct answers.
        second.setCorrect(false); em.flush(); question.setActive(false); em.flush(); publish(id, 400);
        question.setActive(true); em.flush();
        exams.findById(id).orElseThrow().getClasses().clear(); em.flush(); publish(id, 400);
        exams.findById(id).orElseThrow().getClasses().add(schoolClass); em.flush();
        publish(id, 200);
        mvc.perform(get("/api/teacher/exams/{id}", id).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.questionCount").value(1)).andExpect(jsonPath("$.data.options").doesNotExist());
    }

    @Test
    void publicationRejectsExpiredScheduleAndQuestionFromAnotherSubject() throws Exception {
        var payload = request("Expired"); payload.put("startsAt", "2000-01-01T00:00:00Z"); payload.put("endsAt", "2000-01-02T00:00:00Z");
        long id = create(teacherToken, payload).get("id").asLong(); Question question = attachQuestion(id, QuestionType.ESSAY); publish(id, 400);
        Exam exam = exams.findById(id).orElseThrow(); exam.setStartsAt(LocalDateTime.of(2099, 1, 1, 0, 0)); exam.setEndsAt(exam.getStartsAt().plusHours(1));
        Subject second = new Subject(); second.setCode(prefix + "2"); second.setName("Other"); subjects.saveAndFlush(second);
        question.setSubject(second); em.flush(); publish(id, 400);
    }

    @Test
    void stateTransitionsLockPublishedConfigurationAndDoNotReopenClosedExams() throws Exception {
        var payload = request("Transitions"); payload.put("startsAt", "2099-05-01T09:00:00Z"); payload.put("endsAt", "2099-05-01T10:00:00Z");
        long id = create(teacherToken, payload).get("id").asLong(); attachQuestion(id, QuestionType.ESSAY);
        mvc.perform(post("/api/teacher/exams/{id}/close", id).header("Authorization", bearer(teacherToken))).andExpect(status().isConflict());
        publish(id, 200); publish(id, 409);
        mvc.perform(put("/api/teacher/exams/{id}", id).header("Authorization", bearer(teacherToken))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isConflict());
        mvc.perform(delete("/api/teacher/exams/{id}", id).header("Authorization", bearer(teacherToken))).andExpect(status().isConflict());
        mvc.perform(post("/api/teacher/exams/{id}/close", id).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLOSED"));
        publish(id, 409);
        mvc.perform(post("/api/teacher/exams/{id}/close", id).header("Authorization", bearer(teacherToken))).andExpect(status().isConflict());
    }

    @Test
    void draftDeletionRemovesLinksButPreservesBankAndSessionHistory() throws Exception {
        long id = create(teacherToken, request("Delete")).get("id").asLong(); Question question = attachQuestion(id, QuestionType.ESSAY);
        var moved = request("Move subject");
        Subject second = new Subject(); second.setCode(prefix + "2"); second.setName("Other"); subjects.saveAndFlush(second); moved.put("subjectId", second.getId());
        mvc.perform(put("/api/teacher/exams/{id}", id).header("Authorization", bearer(teacherToken))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(moved))).andExpect(status().isConflict());
        mvc.perform(delete("/api/teacher/exams/{id}", id).header("Authorization", bearer(teacherToken))).andExpect(status().isOk());
        assertThat(links.existsByExamId(id)).isFalse(); assertThat(em.find(Question.class, question.getId())).isNotNull();
        long historicalId = create(teacherToken, request("History")).get("id").asLong();
        ExamSession session = new ExamSession(); session.setExam(exams.findById(historicalId).orElseThrow()); session.setStudent(student);
        session.setAttemptNumber(1); session.setStartedAt(LocalDateTime.now(ZoneOffset.UTC)); session.setExpiresAt(session.getStartedAt().plusMinutes(45));
        session.setMaxScore(BigDecimal.TEN); session.setDeviceId(prefix); em.persist(session); em.flush();
        mvc.perform(delete("/api/teacher/exams/{id}", historicalId).header("Authorization", bearer(teacherToken))).andExpect(status().isConflict());
        assertThat(exams.existsById(historicalId)).isTrue();
    }

    @Test
    void accountClassesIncludeAllMembershipsAndSupportPagingAndTeacherLookup() throws Exception {
        schoolClass.getStudents().add(student); otherClass.getStudents().add(student); em.flush();
        mvc.perform(get("/api/admin/users/{id}/classes", student.getId()).header("Authorization", bearer(adminToken))
                .param("page", "2").param("size", "1").param("sortBy", "code:asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pageNumber").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.classes[0].code").value(otherClass.getCode()));
        mvc.perform(get("/api/admin/users/{id}/classes", student.getId()).header("Authorization", bearer(adminToken))
                .param("keyword", schoolClass.getCode()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.classes[0].teacherName").value(teacher.getFullName()));
        // An account can teach and be enrolled in the same class; the class appears only once.
        schoolClass.getStudents().add(teacher); em.flush();
        mvc.perform(get("/api/admin/users/{id}/classes", teacher.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.classes[0].id").value(schoolClass.getId()));
        mvc.perform(get("/api/admin/users/{id}/classes", Long.MAX_VALUE).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentClassLookupUsesAuthenticatedIdentityAndReflectsMembershipChanges() throws Exception {
        mvc.perform(get("/api/student/classes").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        schoolClass.getStudents().add(student); otherClass.getStudents().add(student); em.flush();
        mvc.perform(get("/api/student/classes").header("Authorization", bearer(studentToken))
                .param("userId", other.getId().toString()).param("studentId", other.getId().toString())
                .param("sortBy", "code:asc").param("size", "1").param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.classes[0].id").value(schoolClass.getId()))
                .andExpect(jsonPath("$.data.classes[0].students").doesNotExist())
                .andExpect(jsonPath("$.data.classes[0].teacher.passwordHash").doesNotExist());
        schoolClass.getStudents().clear(); em.flush();
        mvc.perform(get("/api/student/classes").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.classes[0].id").value(otherClass.getId()));
        mvc.perform(get("/api/student/classes").header("Authorization", bearer(studentToken)).param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/student/classes").header("Authorization", bearer(studentToken)).param("sortBy", "teacher.passwordHash:asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teacherRosterIsSearchableAndRestrictedToOwnedClasses() throws Exception {
        schoolClass.getStudents().add(student); em.flush();
        mvc.perform(get("/api/teacher/classes/{id}/students", schoolClass.getId()).header("Authorization", bearer(teacherToken))
                .param("keyword", student.getUsername()).param("sortBy", "username:asc").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.users[0].username").value(student.getUsername()))
                .andExpect(jsonPath("$.data.users[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/teacher/classes/{id}/students", schoolClass.getId()).header("Authorization", bearer(teacherToken))
                .param("keyword", "no-such-student"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/teacher/classes/{id}/students", otherClass.getId()).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/teacher/classes/{id}/students", schoolClass.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/classes/{id}/students", schoolClass.getId()).header("Authorization", bearer(adminToken))
                .param("sortBy", "fullName:asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.users[0].username").value(student.getUsername()));
        mvc.perform(get("/api/teacher/classes/{id}/students", schoolClass.getId()).header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/{id}/classes", student.getId()).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/{id}/classes", other.getId()).header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/student/classes").header("Authorization", bearer(teacherToken))).andExpect(status().isForbidden());
        mvc.perform(get("/api/student/classes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/teacher/classes/{id}/students", 0).header("Authorization", bearer(teacherToken))).andExpect(status().isBadRequest());
    }

    private Map<String, Object> request(String title) {
        var payload = new HashMap<String, Object>(); payload.put("subjectId", subject.getId()); payload.put("title", prefix + " " + title);
        payload.put("durationMinutes", 45); payload.put("maxAttempts", 1); payload.put("resultVisibility", "AFTER_EXAM_END");
        payload.put("shuffleQuestions", false); payload.put("shuffleOptions", false); payload.put("classIds", List.of(schoolClass.getId())); return payload;
    }
    private JsonNode create(String token, Map<String, Object> payload) throws Exception {
        String response = mvc.perform(post("/api/teacher/exams").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("data");
    }
    private void postInvalid(String token, Map<String, Object> payload, int expected) throws Exception {
        mvc.perform(post("/api/teacher/exams").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload))).andExpect(status().is(expected));
    }
    private void publish(long id, int expected) throws Exception {
        mvc.perform(post("/api/teacher/exams/{id}/publish", id).header("Authorization", bearer(teacherToken))).andExpect(status().is(expected));
    }
    private User account(String suffix, RoleName roleName, String hash) {
        User user = new User(); user.setUsername(prefix + suffix); user.setEmail(prefix + suffix + "@example.test");
        user.setFullName(prefix + suffix); user.setPasswordHash(hash); user.setEnabled(true); user.getRoles().add(roles.findByName(roleName).orElseThrow());
        return users.saveAndFlush(user);
    }
    private SchoolClass schoolClass(String suffix, User owner) {
        SchoolClass item = new SchoolClass(); item.setCode(prefix + suffix); item.setName(prefix + suffix); item.setTeacher(owner); return classes.saveAndFlush(item);
    }
    private String login(User user) throws Exception {
        String response = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", user.getUsername(), "password", "Password@123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); return json.readTree(response).get("data").get("token").asText();
    }
    private Question attachQuestion(long id, QuestionType type) {
        Question question = new Question(); question.setSubject(subject); question.setCreatedBy(teacher); question.setContent(prefix);
        question.setType(type); question.setDifficulty(Difficulty.EASY); question.setActive(true); em.persist(question);
        ExamQuestion link = new ExamQuestion(); link.setExam(exams.findById(id).orElseThrow()); link.setQuestion(question);
        link.setDisplayOrder(1); link.setPoints(BigDecimal.TEN); em.persist(link); em.flush(); return question;
    }
    private QuestionOption choice(Question question, String content, boolean correct, int order) {
        QuestionOption option = new QuestionOption(); option.setQuestion(question); option.setContent(content); option.setCorrect(correct);
        option.setDisplayOrder(order); em.persist(option); em.flush(); return option;
    }
    private static String bearer(String token) { return "Bearer " + token; }
}
