package com.techbyte.ExamGuardBE;

import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.RoleName;
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
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class AbstractQuestionBankIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired SubjectRepository subjects;
    @Autowired QuestionRepository questions;
    @Autowired QuestionOptionRepository options;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager em;
    User teacher;
    User other;
    Subject subject;
    String teacherToken;
    String otherToken;
    String adminToken;
    String studentToken;
    String prefix;

    @BeforeEach
    void prepare() throws Exception {
        prefix = "t06" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        for (RoleName name : RoleName.values()) {
            if (roles.findByName(name).isEmpty()) {
                Role role = new Role(); role.setName(name); roles.saveAndFlush(role);
            }
        }
        String hash = passwords.encode("Password@123");
        teacher = account("teacher", RoleName.TEACHER, hash);
        other = account("other", RoleName.TEACHER, hash);
        User admin = account("admin", RoleName.ADMIN, hash);
        User student = account("student", RoleName.STUDENT, hash);
        subject = new Subject(); subject.setCode(prefix.toUpperCase(Locale.ROOT)); subject.setName("Môn học Task 6 " + prefix);
        subjects.saveAndFlush(subject);
        teacherToken = login(teacher); otherToken = login(other); adminToken = login(admin); studentToken = login(student);
    }

    @Test
    void teacherCrudPreservesAndReordersOptionIds() throws Exception {
        var created = create(teacherToken, request("Câu hỏi CRUD", "SINGLE_CHOICE", "EASY"));
        long id = created.get("id").asLong();
        long firstId = created.get("options").get(0).get("id").asLong();
        long secondId = created.get("options").get(1).get("id").asLong();
        assertThat(created.get("createdById").asLong()).isEqualTo(teacher.getId());

        mvc.perform(get("/api/teacher/questions/{id}", id).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.options[0].correct").value(true))
                .andExpect(jsonPath("$.data.createdBy.passwordHash").doesNotExist());

        var update = request("Đã sửa nội dung", "SINGLE_CHOICE", "HARD");
        update.put("active", false);
        update.put("options", List.of(Map.of("id", secondId, "content", "B sửa", "correct", true),
                Map.of("id", firstId, "content", "A sửa", "correct", false), Map.of("content", "C mới", "correct", false)));
        mvc.perform(put("/api/teacher/questions/{id}", id).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value(prefix + " Đã sửa nội dung"))
                .andExpect(jsonPath("$.data.difficulty").value("HARD")).andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.options[0].id").value(secondId))
                .andExpect(jsonPath("$.data.options[0].displayOrder").value(1))
                .andExpect(jsonPath("$.data.options[1].id").value(firstId))
                .andExpect(jsonPath("$.data.options.length()").value(3));

        mvc.perform(delete("/api/teacher/questions/{id}", id).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());
        assertThat(questions.findById(id)).isEmpty();
        assertThat(options.findByQuestionIdOrderByDisplayOrderAsc(id)).isEmpty();
    }

    @Test
    void ownershipAndRolesProtectQuestionsAndAnswerKeys() throws Exception {
        long id = create(teacherToken, request("Riêng tư", "SINGLE_CHOICE", "EASY")).get("id").asLong();
        mvc.perform(get("/api/teacher/questions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/teacher/questions/{id}", id).header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/questions").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/teacher/questions/{id}", id).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/teacher/questions/{id}", id).header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("Sai chủ", "ESSAY", "EASY"))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/teacher/questions/{id}", id).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/teacher/questions").header("Authorization", bearer(otherToken)).param("keyword", prefix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/admin/questions/{id}", id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/admin/questions/{id}", id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void filtersSearchAndPaginationFollowExistingContract() throws Exception {
        create(teacherToken, request("Alpha", "ESSAY", "HARD"));
        create(teacherToken, request("Beta", "ESSAY", "HARD"));
        create(teacherToken, request("Gamma", "ESSAY", "HARD"));
        create(teacherToken, request("Easy", "SINGLE_CHOICE", "EASY"));
        var inactive = request("Inactive", "ESSAY", "HARD"); inactive.put("active", false); create(teacherToken, inactive);
        create(otherToken, request("Other author", "ESSAY", "HARD"));
        mvc.perform(get("/api/teacher/questions").header("Authorization", bearer(teacherToken))
                        .param("keyword", prefix).param("subjectId", subject.getId().toString())
                        .param("difficulty", "HARD").param("type", "ESSAY").param("active", "true")
                        .param("sortBy", "content:asc").param("page", "2").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pageNumber").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(2)).andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.questions.length()").value(1))
                .andExpect(jsonPath("$.data.questions[0].content").value(prefix + " Gamma"));
        mvc.perform(get("/api/admin/questions").header("Authorization", bearer(adminToken))
                        .param("keyword", prefix).param("page", "0").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pageNumber").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(6));
        mvc.perform(get("/api/teacher/questions").header("Authorization", bearer(teacherToken)).param("sortBy", "createdBy.passwordHash:asc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/teacher/questions").header("Authorization", bearer(teacherToken)).param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/teacher/questions").header("Authorization", bearer(teacherToken)).param("difficulty", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void questionValidationRejectsInvalidOptionsAndPayloads() throws Exception {
        var payload = request("Validation", "SINGLE_CHOICE", "EASY");
        for (var invalid : List.of(List.of(Map.of("content", "A", "correct", true)),
                List.of(Map.of("content", "A", "correct", false), Map.of("content", "B", "correct", false)),
                List.of(Map.of("content", "A", "correct", true), Map.of("content", "B", "correct", true)),
                List.of(Map.of("content", "A", "correct", true), Map.of("content", " a ", "correct", false)))) {
            payload.put("options", invalid); postInvalid(payload, 400);
        }
        payload.put("type", "ESSAY"); postInvalid(payload, 400);
        payload.put("options", List.of()); payload.put("content", " "); postInvalid(payload, 400);
        payload.put("content", prefix); payload.put("subjectId", Long.MAX_VALUE); postInvalid(payload, 404);
        payload.put("subjectId", subject.getId()); payload.put("type", "SINGLE_CHOICE");
        payload.put("options", List.of(Map.of("content", " ", "correct", true), Map.of("content", "B", "correct", false)));
        postInvalid(payload, 400);
        assertThat(questions.search("%" + prefix + "%", null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements()).isZero();
    }

    @Test
    void essayTransitionRemovesUnusedOptionsAndCannotStealOptionIds() throws Exception {
        var created = create(teacherToken, request("Chuyển loại", "SINGLE_CHOICE", "EASY"));
        long id = created.get("id").asLong();
        var invalid = request("Sai ID", "SINGLE_CHOICE", "EASY");
        invalid.put("options", List.of(Map.of("id", Long.MAX_VALUE, "content", "A", "correct", true), Map.of("content", "B", "correct", false)));
        mvc.perform(put("/api/teacher/questions/{id}", id).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/teacher/questions/{id}", id).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("Tự luận", "ESSAY", "MEDIUM"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.type").value("ESSAY"))
                .andExpect(jsonPath("$.data.options.length()").value(0));
        assertThat(options.findByQuestionIdOrderByDisplayOrderAsc(id)).isEmpty();
    }

    @Test
    void usedQuestionsCannotBeDeletedOrMovedToAnotherSubjectOrType() throws Exception {
        long id = create(teacherToken, request("Đang dùng", "ESSAY", "MEDIUM")).get("id").asLong();
        Exam exam = new Exam(); exam.setSubject(subject); exam.setTeacher(teacher); exam.setTitle(prefix);
        exam.setDurationMinutes(30); em.persist(exam);
        ExamQuestion link = new ExamQuestion(); link.setExam(exam); link.setQuestion(questions.findById(id).orElseThrow());
        link.setPoints(BigDecimal.ONE); link.setDisplayOrder(1); em.persist(link); em.flush();
        mvc.perform(delete("/api/teacher/questions/{id}", id).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/teacher/questions/{id}", id).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("Đổi loại", "SINGLE_CHOICE", "MEDIUM"))))
                .andExpect(status().isConflict());
        Subject second = new Subject(); second.setCode(prefix + "B"); second.setName("Môn khác"); subjects.saveAndFlush(second);
        var moved = request("Đổi môn", "ESSAY", "MEDIUM"); moved.put("subjectId", second.getId());
        mvc.perform(put("/api/teacher/questions/{id}", id).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(moved)))
                .andExpect(status().isConflict());
        assertThat(questions.findById(id)).isPresent();
    }

    @Test
    void teachersCanReadSubjectCatalogButCannotManageIt() throws Exception {
        mvc.perform(get("/api/teacher/subjects").header("Authorization", bearer(teacherToken)).param("keyword", prefix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.subjects[0].id").value(subject.getId()));
        mvc.perform(post("/api/teacher/subjects").header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/teacher/subjects").header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
    }

    private User account(String suffix, RoleName roleName, String hash) {
        User user = new User(); user.setUsername(prefix + suffix); user.setEmail(prefix + suffix + "@example.test");
        user.setFullName(prefix + suffix); user.setPasswordHash(hash); user.setEnabled(true);
        user.getRoles().add(roles.findByName(roleName).orElseThrow()); return users.saveAndFlush(user);
    }

    private String login(User user) throws Exception {
        String response = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", user.getUsername(), "password", "Password@123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("data").get("token").asText();
    }

    private Map<String, Object> request(String content, String type, String difficulty) {
        Map<String, Object> request = new HashMap<>(); request.put("subjectId", subject.getId());
        request.put("content", prefix + " " + content); request.put("type", type); request.put("difficulty", difficulty);
        request.put("active", true);
        request.put("options", type.equals("ESSAY") ? List.of() : List.of(Map.of("content", "Đáp án A", "correct", true), Map.of("content", "Đáp án B", "correct", false)));
        return request;
    }

    private tools.jackson.databind.JsonNode create(String token, Map<String, Object> request) throws Exception {
        String response = mvc.perform(post("/api/teacher/questions").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("data");
    }

    private void postInvalid(Map<String, Object> request, int expected) throws Exception {
        mvc.perform(post("/api/teacher/questions").header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().is(expected));
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
