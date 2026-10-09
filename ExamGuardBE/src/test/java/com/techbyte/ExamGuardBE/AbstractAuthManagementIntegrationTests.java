package com.techbyte.ExamGuardBE;

import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.RoleName;
import com.techbyte.ExamGuardBE.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class AbstractAuthManagementIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired SubjectRepository subjects;
    @Autowired SchoolClassRepository classes;
    @Autowired PasswordEncoder passwords;

    @BeforeEach
    void seedRoles() {
        for (RoleName name : RoleName.values()) {
            if (roles.findByName(name).isEmpty()) {
                Role role = new Role();
                role.setName(name);
                roles.saveAndFlush(role);
            }
        }
    }

    @Test
    void registrationLoginIntrospectionAndMeUseCommonResponseEnvelope() throws Exception {
        Map<String, Object> registration = Map.of(
                "username", "new_student", "email", "new_student@example.test",
                "password", "Password@123", "fullName", "Nguyễn Văn An",
                "roles", List.of("ADMIN"));

        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(registration)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.user.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        User stored = users.findByUsernameIgnoreCase("new_student").orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2a$10$");
        assertThat(passwords.matches("Password@123", stored.getPasswordHash())).isTrue();
        String token = json.readTree(response).get("data").get("token").asText();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("new_student"));
        mvc.perform(post("/api/auth/introspect").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    void roleSecurityProtectsAdministrationEndpoints() throws Exception {
        createUser("admin_api", RoleName.ADMIN);
        createUser("student_api", RoleName.STUDENT);
        String admin = login("admin_api");
        String student = login("student_api");

        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void userSearchSortAndPaginationMatchReferenceContract() throws Exception {
        createUser("admin_page", RoleName.ADMIN);
        createUser("egtask5_student_alpha", RoleName.STUDENT);
        createUser("egtask5_student_beta", RoleName.STUDENT);
        createUser("egtask5_student_gamma", RoleName.STUDENT);
        String token = login("admin_page");

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token)
                        .param("keyword", "egtask5_student_").param("role", "STUDENT")
                        .param("sortBy", "username:asc").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNumber").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.users.length()").value(1))
                .andExpect(jsonPath("$.data.users[0].username").value("egtask5_student_gamma"));

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token)
                        .param("sortBy", "passwordHash:asc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("sortBy")));
    }

    @Test
    void subjectCrudSupportsSearchAndDuplicateProtection() throws Exception {
        createUser("admin_subject", RoleName.ADMIN);
        String token = login("admin_subject");
        Map<String, Object> request = Map.of("code", "int1344", "name", "Mật mã học cơ sở", "description", "Môn học kiểm thử");
        String created = mvc.perform(post("/api/admin/subjects").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.code").value("INT1344"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("data").get("id").asLong();

        mvc.perform(get("/api/admin/subjects").header("Authorization", "Bearer " + token)
                        .param("keyword", "mật mã").param("page", "1").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(post("/api/admin/subjects").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/subjects/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(subjects.findById(id)).isEmpty();
    }

    @Test
    void classCrudValidatesTeacherAndManagesStudentMembership() throws Exception {
        createUser("admin_class", RoleName.ADMIN);
        User teacher = createUser("teacher_class", RoleName.TEACHER);
        User student = createUser("student_class", RoleName.STUDENT);
        String token = login("admin_class");
        Map<String, Object> request = Map.of("code", "dev-t10", "name", "Lớp phát triển phần mềm", "teacherId", teacher.getId());
        String created = mvc.perform(post("/api/admin/classes").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.code").value("DEV-T10"))
                .andReturn().getResponse().getContentAsString();
        long classId = json.readTree(created).get("data").get("id").asLong();

        mvc.perform(put("/api/admin/classes/{classId}/students/{studentId}", classId, student.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.studentCount").value(1));
        mvc.perform(get("/api/admin/classes/{id}/students", classId).header("Authorization", "Bearer " + token)
                        .param("keyword", "student_class").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.users[0].id").value(student.getId()));
        mvc.perform(delete("/api/admin/classes/{classId}/students/{studentId}", classId, student.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.studentCount").value(0));

        Map<String, Object> invalidTeacher = Map.of("code", "BAD-CLS", "name", "Sai giảng viên", "teacherId", student.getId());
        mvc.perform(post("/api/admin/classes").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalidTeacher)))
                .andExpect(status().isBadRequest());
    }

    private User createUser(String username, RoleName roleName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setFullName(username.replace('_', ' '));
        user.setPasswordHash(passwords.encode("Password@123"));
        user.setEnabled(true);
        user.getRoles().add(roles.findByName(roleName).orElseThrow());
        return users.saveAndFlush(user);
    }

    private String login(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "Password@123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("data").get("token").asText();
    }
}
