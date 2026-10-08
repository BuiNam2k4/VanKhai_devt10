package com.techbyte.ExamGuardBE.auth;

import com.techbyte.ExamGuardBE.entity.User;
import com.techbyte.ExamGuardBE.enums.RoleName;
import com.techbyte.ExamGuardBE.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import static com.techbyte.ExamGuardBE.auth.AuthDtos.*;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    // A valid BCrypt hash ensures unknown users also perform the password comparison.
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu không được vượt quá 72 byte UTF-8");
        }
        String username = request.username().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username) || users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập hoặc email đã được sử dụng");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwords.encode(request.password()));
        user.getRoles().add(roles.findByName(RoleName.STUDENT).orElseThrow(() ->
                new IllegalStateException("Missing STUDENT role; initialize db/data.sql")));
        return tokens.issue(users.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByUsernameIgnoreCase(request.username().trim()).orElse(null);
        boolean valid = request.password().getBytes(StandardCharsets.UTF_8).length <= 72
                && passwords.matches(request.password(), user == null ? DUMMY_HASH : user.getPasswordHash());
        if (!valid || user == null || !user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng, hoặc tài khoản đã bị khóa");
        }
        return tokens.issue(user);
    }
}
