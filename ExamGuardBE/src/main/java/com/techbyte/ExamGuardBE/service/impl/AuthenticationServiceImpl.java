package com.techbyte.ExamGuardBE.service.impl;

import com.techbyte.ExamGuardBE.controller.request.*;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.RoleName;
import com.techbyte.ExamGuardBE.exception.*;
import com.techbyte.ExamGuardBE.mapper.ManagementMapper;
import com.techbyte.ExamGuardBE.repository.*;
import com.techbyte.ExamGuardBE.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "AUTHENTICATION-SERVICE")
public class AuthenticationServiceImpl implements AuthenticationService {
    private static final String DUMMY_BCRYPT = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    @Value("${jwt.issuer}")
    private String issuer;
    @Value("${jwt.expiration-seconds}")
    private long expirationSeconds;

    @Override
    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        checkPasswordLength(request.getPassword());
        String username = request.getUsername().trim().toLowerCase(Locale.ROOT);
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username) || userRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.USER_EXISTS);
        }
        Role studentRole = roleRepository.findByName(RoleName.STUDENT)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.getFullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(true);
        user.getRoles().add(studentRole);
        userRepository.saveAndFlush(user);
        log.info("Registered student username={}", username);
        return issueToken(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        checkPasswordLength(request.getPassword());
        User user = userRepository.findByUsernameIgnoreCase(request.getUsername().trim()).orElse(null);
        boolean matches = passwordEncoder.matches(request.getPassword(), user == null ? DUMMY_BCRYPT : user.getPasswordHash());
        if (!matches || user == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        if (!user.isEnabled()) throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        log.info("Authenticated username={}", user.getUsername());
        return issueToken(user);
    }

    @Override
    @Transactional(readOnly = true)
    public IntrospectResponse introspect(IntrospectRequest request) {
        try {
            Jwt jwt = jwtDecoder.decode(request.getToken());
            long userId = Long.parseLong(jwt.getSubject());
            boolean valid = userRepository.findWithRolesById(userId).filter(User::isEnabled).isPresent();
            return IntrospectResponse.builder().valid(valid).build();
        } catch (JwtException | NumberFormatException exception) {
            return IntrospectResponse.builder().valid(false).build();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return ManagementMapper.toUserResponse(user);
    }

    private AuthenticationResponse issueToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);
        UserResponse profile = ManagementMapper.toUserResponse(user);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(user.getId().toString()).issuedAt(now).expiresAt(expiresAt)
                .id(UUID.randomUUID().toString()).claim("username", user.getUsername())
                .claim("roles", profile.getRoles().stream().map(Enum::name).sorted().toList()).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS512).build(), claims)).getTokenValue();
        return AuthenticationResponse.builder().token(token).tokenType("Bearer")
                .authenticated(true).expiresAt(expiresAt).user(profile).build();
    }

    private static void checkPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
    }
}
