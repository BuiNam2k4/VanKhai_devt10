package com.techbyte.ExamGuardBE.mapper;

import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.RoleName;

import java.util.stream.Collectors;

public final class ManagementMapper {
    private ManagementMapper() {}

    public static UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId()).username(user.getUsername()).email(user.getEmail())
                .fullName(user.getFullName()).enabled(user.isEnabled())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt()).updatedAt(user.getUpdatedAt()).build();
    }

    public static SubjectResponse toSubjectResponse(Subject subject) {
        return SubjectResponse.builder().id(subject.getId()).code(subject.getCode())
                .name(subject.getName()).description(subject.getDescription())
                .createdAt(subject.getCreatedAt()).updatedAt(subject.getUpdatedAt()).build();
    }

    public static SchoolClassResponse toClassResponse(SchoolClass schoolClass) {
        User teacher = schoolClass.getTeacher();
        return SchoolClassResponse.builder().id(schoolClass.getId()).code(schoolClass.getCode())
                .name(schoolClass.getName()).teacherId(teacher.getId()).teacherName(teacher.getFullName())
                .studentCount(schoolClass.getStudents().size())
                .createdAt(schoolClass.getCreatedAt()).updatedAt(schoolClass.getUpdatedAt()).build();
    }

    public static boolean hasRole(User user, RoleName roleName) {
        return user.getRoles().stream().anyMatch(role -> role.getName() == roleName);
    }
}
