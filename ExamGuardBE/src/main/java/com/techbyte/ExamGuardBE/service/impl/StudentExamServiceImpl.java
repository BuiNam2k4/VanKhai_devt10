package com.techbyte.ExamGuardBE.service.impl;

import com.techbyte.ExamGuardBE.common.*;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.*;
import com.techbyte.ExamGuardBE.exception.*;
import com.techbyte.ExamGuardBE.mapper.ManagementMapper;
import com.techbyte.ExamGuardBE.repository.*;
import com.techbyte.ExamGuardBE.service.StudentExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentExamServiceImpl implements StudentExamService {
    private static final Set<String> SORT_FIELDS = Set.of("id", "title", "startsAt", "endsAt", "durationMinutes", "createdAt");
    private final ExamRepository exams;
    private final ExamSessionRepository sessions;
    private final ExamQuestionRepository questions;

    @Override
    public StudentExamPageResponse findAll(UserResponse actor, String keyword, Long subjectId, ExamStatus status,
            ExamWindow window, String sortBy, int page, int size) {
        checkRole(actor);
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var pageable = PaginationUtils.pageable(page, size, sortBy, SORT_FIELDS);
        String search = keyword == null || keyword.isBlank() ? "" : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        var result = exams.searchForStudent(actor.getId(), search, subjectId, status, window == null ? null : window.name(), now, pageable);
        var response = new StudentExamPageResponse(); response.setServerTime(utc(now));
        response.setExams(map(actor.getId(), result.getContent(), now));
        PaginationUtils.fill(response, page, size, result); return response;
    }

    @Override
    public StudentExamResponse findById(UserResponse actor, Long id) {
        checkRole(actor);
        var exam = exams.findVisibleToStudent(id, actor.getId()).orElseThrow(() -> new AppException(ErrorCode.EXAM_NOT_FOUND));
        return map(actor.getId(), List.of(exam), LocalDateTime.now(ZoneOffset.UTC)).getFirst();
    }

    @Override
    public SubjectPageResponse subjects(UserResponse actor, int page, int size) {
        checkRole(actor);
        var result = exams.findSubjectsForStudent(actor.getId(), PaginationUtils.pageable(page, size, "code:asc", Set.of("code")));
        var response = new SubjectPageResponse(); response.setSubjects(result.getContent().stream().map(ManagementMapper::toSubjectResponse).toList());
        PaginationUtils.fill(response, page, size, result); return response;
    }

    private List<StudentExamResponse> map(Long studentId, List<Exam> rows, LocalDateTime now) {
        if (rows.isEmpty()) return List.of();
        var ids = rows.stream().map(Exam::getId).toList();
        var used = sessions.countStudentAttempts(studentId, ids).stream().collect(Collectors.toMap(ExamSessionRepository.AttemptCount::getExamId, ExamSessionRepository.AttemptCount::getAttemptsUsed));
        var active = sessions.findActiveStudentAttempts(studentId, ids).stream().collect(Collectors.toMap(session -> session.getExam().getId(), session -> session));
        var counts = questions.countByExamIds(ids).stream().collect(Collectors.toMap(ExamQuestionRepository.ExamQuestionCount::getExamId, ExamQuestionRepository.ExamQuestionCount::getQuestionCount));
        return rows.stream().map(exam -> {
            long attempts = used.getOrDefault(exam.getId(), 0L); var session = active.get(exam.getId());
            var participation = StudentExamEligibility.evaluate(exam, attempts, session, now);
            return new StudentExamResponse(exam.getId(), exam.getTitle(), exam.getDescription(), exam.getSubject().getId(), exam.getSubject().getCode(),
                exam.getSubject().getName(), exam.getTeacher().getFullName(), exam.getStatus(), utc(exam.getStartsAt()), utc(exam.getEndsAt()),
                exam.getDurationMinutes(), exam.getMaxAttempts(), Math.toIntExact(counts.getOrDefault(exam.getId(), 0L)), attempts,
                Math.max(0L, exam.getMaxAttempts() - attempts), participation, StudentExamEligibility.message(participation),
                participation == ExamParticipationStatus.AVAILABLE, participation == ExamParticipationStatus.IN_PROGRESS,
                session == null ? null : session.getId(), session == null ? null : utc(session.getExpiresAt()), exam.getResultVisibility(), utc(now));
        }).toList();
    }
    private static OffsetDateTime utc(LocalDateTime value) { return value == null ? null : value.atOffset(ZoneOffset.UTC); }
    private static void checkRole(UserResponse actor) { if (!actor.getRoles().contains(RoleName.STUDENT)) throw new AppException(ErrorCode.FORBIDDEN); }
}
