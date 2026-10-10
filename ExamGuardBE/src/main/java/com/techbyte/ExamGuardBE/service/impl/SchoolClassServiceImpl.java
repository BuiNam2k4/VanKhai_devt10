package com.techbyte.ExamGuardBE.service.impl;

import com.techbyte.ExamGuardBE.common.PaginationUtils;
import com.techbyte.ExamGuardBE.controller.request.SchoolClassRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.RoleName;
import com.techbyte.ExamGuardBE.exception.*;
import com.techbyte.ExamGuardBE.mapper.ManagementMapper;
import com.techbyte.ExamGuardBE.repository.*;
import com.techbyte.ExamGuardBE.service.SchoolClassService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "CLASS-SERVICE")
public class SchoolClassServiceImpl implements SchoolClassService {
    private static final Set<String> CLASS_SORT_FIELDS = Set.of("id", "code", "name", "createdAt");
    private static final Set<String> USER_SORT_FIELDS = Set.of("id", "username", "email", "fullName", "createdAt");
    private final SchoolClassRepository classRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public SchoolClassPageResponse findAll(String keyword, Long teacherId, String sortBy, int page, int size) {
        var pageable = PaginationUtils.pageable(page, size, sortBy, CLASS_SORT_FIELDS);
        String search = search(keyword);
        Page<SchoolClass> result = classRepository.search(search, teacherId, pageable);
        SchoolClassPageResponse response = new SchoolClassPageResponse();
        response.setClasses(result.getContent().stream().map(ManagementMapper::toClassResponse).toList());
        PaginationUtils.fill(response, page, size, result);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolClassResponse findById(Long id) {
        return ManagementMapper.toClassResponse(getClass(id));
    }

    @Override
    @Transactional
    public SchoolClassResponse save(SchoolClassRequest request) {
        String code = normalizeCode(request.getCode());
        if (classRepository.existsByCodeIgnoreCase(code)) throw new AppException(ErrorCode.CLASS_EXISTS);
        SchoolClass schoolClass = new SchoolClass();
        copy(request, schoolClass, code);
        log.info("Creating class code={}", code);
        return ManagementMapper.toClassResponse(classRepository.saveAndFlush(schoolClass));
    }

    @Override
    @Transactional
    public SchoolClassResponse update(Long id, SchoolClassRequest request) {
        SchoolClass schoolClass = getClass(id);
        String code = normalizeCode(request.getCode());
        if (classRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) throw new AppException(ErrorCode.CLASS_EXISTS);
        copy(request, schoolClass, code);
        log.info("Updating class id={}", id);
        return ManagementMapper.toClassResponse(classRepository.saveAndFlush(schoolClass));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SchoolClass schoolClass = getClass(id);
        schoolClass.getStudents().clear();
        classRepository.saveAndFlush(schoolClass);
        log.info("Deleting class id={}", id);
        classRepository.delete(schoolClass);
        classRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public UserPageResponse findStudents(Long classId, String keyword, String sortBy, int page, int size) {
        if (!classRepository.existsById(classId)) throw new AppException(ErrorCode.CLASS_NOT_FOUND);
        var pageable = PaginationUtils.pageable(page, size, sortBy, USER_SORT_FIELDS);
        Page<User> result = classRepository.searchStudents(classId, search(keyword), pageable);
        UserPageResponse response = new UserPageResponse();
        response.setUsers(result.getContent().stream().map(ManagementMapper::toUserResponse).toList());
        PaginationUtils.fill(response, page, size, result);
        return response;
    }

    @Override
    @Transactional
    public SchoolClassResponse addStudent(Long classId, Long studentId) {
        SchoolClass schoolClass = getClass(classId);
        User student = getUser(studentId);
        if (!ManagementMapper.hasRole(student, RoleName.STUDENT)) throw new AppException(ErrorCode.INVALID_STUDENT);
        boolean exists = schoolClass.getStudents().stream().anyMatch(item -> item.getId().equals(studentId));
        if (exists) throw new AppException(ErrorCode.STUDENT_ALREADY_IN_CLASS);
        schoolClass.getStudents().add(student);
        log.info("Adding student id={} to class id={}", studentId, classId);
        return ManagementMapper.toClassResponse(classRepository.saveAndFlush(schoolClass));
    }

    @Override
    @Transactional
    public SchoolClassResponse removeStudent(Long classId, Long studentId) {
        SchoolClass schoolClass = getClass(classId);
        boolean removed = schoolClass.getStudents().removeIf(item -> item.getId().equals(studentId));
        if (!removed) throw new AppException(ErrorCode.STUDENT_NOT_IN_CLASS);
        log.info("Removing student id={} from class id={}", studentId, classId);
        return ManagementMapper.toClassResponse(classRepository.saveAndFlush(schoolClass));
    }

    private void copy(SchoolClassRequest request, SchoolClass schoolClass, String code) {
        User teacher = getUser(request.getTeacherId());
        if (!ManagementMapper.hasRole(teacher, RoleName.TEACHER)) throw new AppException(ErrorCode.INVALID_TEACHER);
        schoolClass.setCode(code);
        schoolClass.setName(request.getName().trim());
        schoolClass.setTeacher(teacher);
    }

    private SchoolClass getClass(Long id) {
        return classRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));
    }

    private User getUser(Long id) {
        return userRepository.findWithRolesById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private static String search(String keyword) {
        return keyword == null || keyword.isBlank() ? "" : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
