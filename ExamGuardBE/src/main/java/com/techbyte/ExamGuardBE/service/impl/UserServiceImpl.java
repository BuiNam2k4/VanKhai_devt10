package com.techbyte.ExamGuardBE.service.impl;

import com.techbyte.ExamGuardBE.common.PaginationUtils;
import com.techbyte.ExamGuardBE.controller.request.*;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.RoleName;
import com.techbyte.ExamGuardBE.exception.*;
import com.techbyte.ExamGuardBE.mapper.ManagementMapper;
import com.techbyte.ExamGuardBE.repository.*;
import com.techbyte.ExamGuardBE.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "USER-SERVICE")
public class UserServiceImpl implements UserService {
    private static final Set<String> SORT_FIELDS = Set.of("id", "username", "email", "fullName", "enabled", "createdAt");
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserPageResponse findAll(String keyword, RoleName role, Boolean enabled, String sortBy, int page, int size) {
        var pageable = PaginationUtils.pageable(page, size, sortBy, SORT_FIELDS);
        String search = keyword == null || keyword.isBlank() ? "" : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        Page<User> result = userRepository.search(search, role, enabled, pageable);
        UserPageResponse response = new UserPageResponse();
        response.setUsers(result.getContent().stream().map(ManagementMapper::toUserResponse).toList());
        PaginationUtils.fill(response, page, size, result);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return ManagementMapper.toUserResponse(getUser(id));
    }

    @Override
    @Transactional
    public UserResponse save(UserCreationRequest request) {
        checkPassword(request.getPassword());
        String username = normalizeUsername(request.getUsername());
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByUsernameIgnoreCase(username) || userRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.USER_EXISTS);
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.getFullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(request.isEnabled());
        setRoles(user, request.getRoles());
        log.info("Creating user username={}", username);
        return ManagementMapper.toUserResponse(userRepository.saveAndFlush(user));
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = getUser(id);
        String username = normalizeUsername(request.getUsername());
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(username, id)
                || userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new AppException(ErrorCode.USER_EXISTS);
        }
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.getFullName().trim());
        user.setEnabled(request.isEnabled());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            checkPassword(request.getPassword());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        setRoles(user, request.getRoles());
        log.info("Updating user id={}", id);
        return ManagementMapper.toUserResponse(userRepository.saveAndFlush(user));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = getUser(id);
        log.info("Deleting user id={}", id);
        userRepository.delete(user);
        userRepository.flush();
    }

    private User getUser(Long id) {
        return userRepository.findWithRolesById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private void setRoles(User user, Set<RoleName> roleNames) {
        List<Role> roles = roleRepository.findByNameIn(roleNames);
        if (roles.size() != roleNames.size()) throw new AppException(ErrorCode.ROLE_NOT_FOUND);
        user.getRoles().clear();
        user.getRoles().addAll(roles);
    }

    private static void checkPassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
    }

    private static String normalizeUsername(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
