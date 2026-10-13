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

    @Test
    void everyRoleCanEditOnlyTheirOwnProfileWithoutChangingPrivilegesOrPassword() throws Exception {
        String prefix = "profile" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        User unrelated = createUser(prefix + "other", RoleName.STUDENT);
        for (RoleName role : RoleName.values()) {
            User user = createUser(prefix + role.name().toLowerCase(Locale.ROOT), role);
            String token = login(user.getUsername()); String hash = user.getPasswordHash(); String oldUsername = user.getUsername();
            String renamed = oldUsername + "new";
            var payload = new HashMap<String, Object>(); payload.put("fullName", "  Họ tên đã chỉnh sửa  "); payload.put("username", renamed.toUpperCase(Locale.ROOT));
            payload.put("email", renamed.toUpperCase(Locale.ROOT) + "@EXAMPLE.TEST");
            payload.put("id", unrelated.getId()); payload.put("roles", List.of("ADMIN")); payload.put("enabled", false); payload.put("password", "Injected@123");
            mvc.perform(put("/api/auth/me").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(user.getId()))
                    .andExpect(jsonPath("$.data.fullName").value("Họ tên đã chỉnh sửa"))
                    .andExpect(jsonPath("$.data.username").value(renamed)).andExpect(jsonPath("$.data.email").value(renamed + "@example.test"))
                    .andExpect(jsonPath("$.data.roles[0]").value(role.name())).andExpect(jsonPath("$.data.enabled").value(true))
                    .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(renamed));
            assertThat(users.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
            assertThat(users.findById(unrelated.getId()).orElseThrow().getUsername()).isEqualTo(prefix + "other");
            authenticate(oldUsername, "Password@123", 401); authenticate(renamed, "Password@123", 200);
        }
    }

    @Test
    void profileDuplicatesAndValidationDoNotPartiallyChangeTheAccount() throws Exception {
        var user = createUser("settings_self", RoleName.STUDENT); var other = createUser("settings_other", RoleName.STUDENT); String token = login(user.getUsername());
        var values = new HashMap<String, Object>(); values.put("fullName", "Changed"); values.put("username", other.getUsername().toUpperCase(Locale.ROOT)); values.put("email", user.getEmail());
        updateProfile(token, values, 409);
        values.put("username", user.getUsername()); values.put("email", other.getEmail().toUpperCase(Locale.ROOT)); updateProfile(token, values, 409);
        values.put("email", "invalid-email"); updateProfile(token, values, 400);
        values.put("email", user.getEmail()); values.put("username", "invalid username"); updateProfile(token, values, 400);
        values.put("username", user.getUsername()); values.put("fullName", " "); updateProfile(token, values, 400);
        assertThat(users.findById(user.getId()).orElseThrow().getFullName()).isEqualTo("settings self");
        mvc.perform(put("/api/auth/me").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(values))).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeChecksCurrentConfirmationLengthAndUtf8LimitWithoutChangingHashOnFailure() throws Exception {
        var user = createUser("settings_password", RoleName.STUDENT); String token = login(user.getUsername()); String hash = user.getPasswordHash();
        changePassword(token, "Wrong@123", "NewPassword@123", "NewPassword@123", 400);
        changePassword(token, "Password@123", "NewPassword@123", "Different@123", 400);
        changePassword(token, "Password@123", "short", "short", 400);
        changePassword(token, "Password@123", "á".repeat(37), "á".repeat(37), 400);
        changePassword(token, "á".repeat(37), "NewPassword@123", "NewPassword@123", 400);
        assertThat(users.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
        authenticate(user.getUsername(), "Password@123", 200);
    }

    @Test
    void passwordChangeInvalidatesAllOldTokensAndKeepsIdentityAndRoles() throws Exception {
        var user = createUser("settings_change", RoleName.TEACHER); String first = login(user.getUsername()); String second = login(user.getUsername());
        changePassword(first, "Password@123", "NewPassword@123", "NewPassword@123", 200);
        var stored = users.findById(user.getId()).orElseThrow();
        assertThat(passwords.matches("NewPassword@123", stored.getPasswordHash())).isTrue();
        assertThat(stored.getRoles()).extracting(Role::getName).containsExactly(RoleName.TEACHER);
        for (String token : List.of(first, second)) {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/introspect").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token", token))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(false));
        }
        authenticate(stored.getUsername(), "Password@123", 401);
        String newToken = authenticate(stored.getUsername(), "NewPassword@123", 200);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + newToken)).andExpect(status().isOk());
        String claims = new String(Base64.getUrlDecoder().decode(newToken.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(claims).doesNotContain(stored.getPasswordHash(), "passwordHash");
        assertThat(json.readTree(claims).get("credentialStamp").asText()).hasSize(43);
    }

    @Test
    void administratorPasswordResetAlsoInvalidatesTheAffectedUsersOldToken() throws Exception {
        var admin = createUser("settings_admin", RoleName.ADMIN); var student = createUser("settings_reset", RoleName.STUDENT);
        String adminToken = login(admin.getUsername()); String studentToken = login(student.getUsername());
        var body = Map.of("username", student.getUsername(), "email", student.getEmail(), "fullName", student.getFullName(),
                "roles", List.of("STUDENT"), "enabled", true, "password", "ResetPassword@123");
        mvc.perform(put("/api/admin/users/{id}", student.getId()).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + studentToken)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        authenticate(student.getUsername(), "ResetPassword@123", 200);
    }

    @Test
    void settingsRequireAuthenticationAndDisabledAccountsCannotUseExistingTokens() throws Exception {
        var user = createUser("settings_disabled", RoleName.STUDENT); String token = login(user.getUsername());
        var body = Map.of("currentPassword", "Password@123", "newPassword", "NewPassword@123", "confirmPassword", "NewPassword@123");
        mvc.perform(put("/api/auth/password").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isUnauthorized());
        user.setEnabled(false); users.saveAndFlush(user);
        changePassword(token, "Password@123", "NewPassword@123", "NewPassword@123", 401);
        updateProfile(token, Map.of("fullName", "Changed", "username", user.getUsername(), "email", user.getEmail()), 401);
    }

    private void updateProfile(String token, Map<String, Object> body, int expected) throws Exception {
        mvc.perform(put("/api/auth/me").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expected));
    }
    private void changePassword(String token, String current, String next, String confirmation, int expected) throws Exception {
        mvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", current, "newPassword", next, "confirmPassword", confirmation))))
                .andExpect(status().is(expected));
    }
    private String authenticate(String username, String password, int expected) throws Exception {
        var response = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return expected == 200 ? json.readTree(response).get("data").get("token").asText() : null;
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
