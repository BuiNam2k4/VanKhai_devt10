package com.techbyte.ExamGuardBE;

import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.RoleName;
import com.techbyte.ExamGuardBE.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class AuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;

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
    void registrationHashesPasswordReturnsJwtAndNeverGrantsRequestedAdminRole() throws Exception {
        var body = registration();
        body.put("roles", List.of("ADMIN"));
        body.put("enabled", false);
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.user.roles.length()").value(1))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.username").value("auth_new"))
                .andReturn().getResponse().getContentAsString();
        User user = users.findByUsernameIgnoreCase("auth_new").orElseThrow();
        assertThat(user.getPasswordHash()).startsWith("$2a$12$").isNotEqualTo("Password@123");
        assertThat(passwords.matches("Password@123", user.getPasswordHash())).isTrue();
        assertThat(user.isEnabled()).isTrue();
        String token = json.readTree(response).get("accessToken").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.getId()));
        mvc.perform(get("/api/admin/profile").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void duplicateUsernameAndEmailAreRejectedIgnoringCase() throws Exception {
        createUser(RoleName.STUDENT);
        var body = registration();
        body.put("username", "AUTH_STUDENT");
        postRegistration(body, 409);
        body.put("username", "another_student");
        body.put("email", "AUTH_STUDENT@EXAMPLE.TEST");
        postRegistration(body, 409);
    }

    @Test
    void invalidRegistrationIsRejectedWithoutLeakingPassword() throws Exception {
        var body = registration();
        body.put("email", "invalid");
        body.put("password", "short");
        body.put("fullName", "  ");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists()).andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("short"))));
    }

    @Test
    void unicodePasswordCannotExceedBcryptByteLimit() throws Exception {
        var body = registration();
        body.put("password", "ệ".repeat(25));
        postRegistration(body, 400);
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
    }

    @ParameterizedTest
    @EnumSource(RoleName.class)
    void loginAndRoleMatrixAreEnforced(RoleName role) throws Exception {
        createUser(role);
        String token = login("AUTH_" + role.name());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value(role.name()));
        for (RoleName target : RoleName.values()) {
            boolean allowed = role == target || (role == RoleName.ADMIN && target == RoleName.TEACHER);
            mvc.perform(get("/api/" + target.name().toLowerCase(Locale.ROOT) + "/profile").header("Authorization", "Bearer " + token))
                    .andExpect(status().is(allowed ? 200 : 403));
        }
    }

    @Test
    void wrongPasswordUnknownAndDisabledAccountsReturnSameError() throws Exception {
        User user = createUser(RoleName.STUDENT);
        String wrong = rejectedLogin("auth_student", "wrong-password");
        assertThat(rejectedLogin("not_present", "wrong-password")).isEqualTo(wrong);
        user.setEnabled(false);
        users.saveAndFlush(user);
        assertThat(rejectedLogin("auth_student", "Password@123")).isEqualTo(wrong);
    }

    @Test
    void lockingAccountInvalidatesExistingToken() throws Exception {
        User user = createUser(RoleName.STUDENT);
        String token = login(user.getUsername());
        user.setEnabled(false);
        users.saveAndFlush(user);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void roleChangesApplyToExistingToken() throws Exception {
        User user = createUser(RoleName.ADMIN);
        String token = login(user.getUsername());
        user.getRoles().clear();
        user.getRoles().add(roles.findByName(RoleName.STUDENT).orElseThrow());
        users.saveAndFlush(user);
        mvc.perform(get("/api/admin/profile").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/student/profile").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void missingMalformedAndTamperedTokensAreUnauthorized() throws Exception {
        createUser(RoleName.STUDENT);
        String token = login("auth_student");
        String[] segments = token.split("\\.");
        String tampered = segments[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"1\",\"roles\":[\"ADMIN\"]}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + segments[2];
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
        for (String invalid : List.of("invalid", tampered)) {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + invalid))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void expiredWrongIssuerMissingExpiryAndInvalidSubjectsAreRejected() throws Exception {
        User user = createUser(RoleName.STUDENT);
        Instant now = Instant.now();
        List<JwtClaimsSet> invalidClaims = List.of(
                JwtClaimsSet.builder().issuer("examguard").subject(user.getId().toString()).issuedAt(now.minusSeconds(100)).expiresAt(now.minusSeconds(1)).build(),
                JwtClaimsSet.builder().issuer("other-issuer").subject(user.getId().toString()).issuedAt(now).expiresAt(now.plusSeconds(60)).build(),
                JwtClaimsSet.builder().issuer("examguard").subject(user.getId().toString()).issuedAt(now).build(),
                JwtClaimsSet.builder().issuer("examguard").subject("not-an-id").issuedAt(now).expiresAt(now.plusSeconds(60)).build(),
                JwtClaimsSet.builder().issuer("examguard").subject("9223372036854775807").issuedAt(now).expiresAt(now.plusSeconds(60)).build());
        for (JwtClaimsSet claims : invalidClaims) {
            String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void corsAllowsConfiguredFrontendAndRejectsUnknownOrigin() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/auth/login").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private User createUser(RoleName role) {
        User user = new User();
        user.setUsername("auth_" + role.name().toLowerCase(Locale.ROOT));
        user.setEmail(user.getUsername() + "@example.test");
        user.setFullName("Kiểm thử xác thực");
        user.setPasswordHash(passwords.encode("Password@123"));
        user.getRoles().add(roles.findByName(role).orElseThrow());
        return users.saveAndFlush(user);
    }

    private Map<String, Object> registration() {
        return new HashMap<>(Map.of("username", "AUTH_NEW", "email", "auth_new@example.test", "password", "Password@123", "fullName", "Phạm Văn Khải"));
    }

    private void postRegistration(Map<String, Object> body, int status) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(status));
    }

    private String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "Password@123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private String rejectedLogin(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
    }
}
