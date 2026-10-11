package com.techbyte.ExamGuardBE.service.impl;

import com.techbyte.ExamGuardBE.common.PaginationUtils;
import com.techbyte.ExamGuardBE.controller.request.ExamRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.*;
import com.techbyte.ExamGuardBE.exception.*;
import com.techbyte.ExamGuardBE.mapper.*;
import com.techbyte.ExamGuardBE.repository.*;
import com.techbyte.ExamGuardBE.service.ExamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "EXAM-SERVICE")
public class ExamServiceImpl implements ExamService {
    private static final Set<String> SORT_FIELDS = Set.of("id", "title", "status", "startsAt", "endsAt", "durationMinutes", "maxAttempts", "createdAt", "updatedAt");
    private final ExamRepository exams;
    private final ExamQuestionRepository examQuestions;
    private final QuestionOptionRepository options;
    private final SubjectRepository subjects;
    private final SchoolClassRepository classes;
    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public ExamPageResponse findAll(UserResponse actor, String keyword, Long subjectId, ExamStatus status,
                                    Long teacherId, String sortBy, int page, int size) {
        checkRole(actor);
        var pageable = PaginationUtils.pageable(page, size, sortBy, SORT_FIELDS);
        String search = keyword == null || keyword.isBlank() ? "" : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        var result = exams.search(search, subjectId, status, isAdmin(actor) ? teacherId : actor.getId(), pageable);
        var ids = result.getContent().stream().map(Exam::getId).toList();
        Map<Long, Integer> counts = Map.of();
        if (!ids.isEmpty()) {
            // Fetch collections only after paging; fetching them in the paged query would page in memory.
            exams.findWithClassesByIdIn(ids);
            counts = examQuestions.countByExamIds(ids).stream().collect(Collectors.toMap(
                    ExamQuestionRepository.ExamQuestionCount::getExamId, item -> Math.toIntExact(item.getQuestionCount())));
        }
        var questionCounts = counts;
        ExamPageResponse response = new ExamPageResponse();
        response.setExams(result.getContent().stream().map(exam -> ExamMapper.toResponse(exam,
                questionCounts.getOrDefault(exam.getId(), 0))).toList());
        PaginationUtils.fill(response, page, size, result);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ExamResponse findById(UserResponse actor, Long id) { return response(getExam(actor, id, false)); }

    @Override
    @Transactional
    public ExamResponse save(UserResponse actor, ExamRequest request) {
        checkRole(actor);
        Exam exam = new Exam();
        copy(actor, request, exam);
        exam.setStatus(ExamStatus.DRAFT);
        exams.saveAndFlush(exam);
        log.info("Created draft exam id={} teacherId={}", exam.getId(), exam.getTeacher().getId());
        return response(exam);
    }

    @Override
    @Transactional
    public ExamResponse update(UserResponse actor, Long id, ExamRequest request) {
        Exam exam = getExam(actor, id, true);
        requireDraft(exam);
        if (!exam.getSubject().getId().equals(request.getSubjectId()) && examQuestions.existsByExamId(id)) {
            throw new AppException(ErrorCode.EXAM_SUBJECT_IN_USE);
        }
        copy(actor, request, exam);
        exams.saveAndFlush(exam);
        log.info("Updated exam id={} actorId={}", id, actor.getId());
        return response(exam);
    }

    @Override
    @Transactional
    public ExamResponse publish(UserResponse actor, Long id) {
        Exam exam = getExam(actor, id, true);
        if (exam.getStatus() != ExamStatus.DRAFT) throw new AppException(ErrorCode.INVALID_EXAM_TRANSITION);
        validatePublish(exam);
        exam.setStatus(ExamStatus.PUBLISHED);
        exams.saveAndFlush(exam);
        log.info("Published exam id={} actorId={}", id, actor.getId());
        return response(exam);
    }

    @Override
    @Transactional
    public ExamResponse close(UserResponse actor, Long id) {
        Exam exam = getExam(actor, id, true);
        if (exam.getStatus() != ExamStatus.PUBLISHED) throw new AppException(ErrorCode.INVALID_EXAM_TRANSITION);
        exam.setStatus(ExamStatus.CLOSED);
        exams.saveAndFlush(exam);
        log.info("Closed exam id={} actorId={}", id, actor.getId());
        return response(exam);
    }

    @Override
    @Transactional
    public void delete(UserResponse actor, Long id) {
        Exam exam = getExam(actor, id, true);
        requireDraft(exam);
        if (exams.hasSessions(id)) throw new AppException(ErrorCode.RESOURCE_IN_USE);
        examQuestions.deleteAll(examQuestions.findByExamIdOrderByDisplayOrderAsc(id));
        examQuestions.flush();
        exam.getClasses().clear();
        exams.delete(exam);
        exams.flush();
        log.info("Deleted draft exam id={} actorId={}", id, actor.getId());
    }

    private void copy(UserResponse actor, ExamRequest request, Exam exam) {
        validateSchedule(request.getStartsAt(), request.getEndsAt());
        Long teacherId = isAdmin(actor) ? request.getTeacherId() : actor.getId();
        if (!isAdmin(actor) && request.getTeacherId() != null && !actor.getId().equals(request.getTeacherId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (teacherId == null) throw new AppException(ErrorCode.INVALID_TEACHER);
        User teacher = users.findWithRolesById(teacherId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!teacher.isEnabled() || !ManagementMapper.hasRole(teacher, RoleName.TEACHER)) throw new AppException(ErrorCode.INVALID_TEACHER);
        Subject subject = subjects.findById(request.getSubjectId()).orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));
        var assigned = classes.findAllById(request.getClassIds());
        if (assigned.size() != request.getClassIds().size()) throw new AppException(ErrorCode.CLASS_NOT_FOUND);
        if (assigned.stream().anyMatch(item -> !item.getTeacher().getId().equals(teacherId))) {
            throw new AppException(ErrorCode.INVALID_EXAM_CLASS);
        }
        exam.setTeacher(teacher); exam.setSubject(subject);
        exam.setTitle(request.getTitle().trim());
        exam.setDescription(request.getDescription() == null || request.getDescription().isBlank() ? null : request.getDescription().trim());
        exam.setStartsAt(utc(request.getStartsAt())); exam.setEndsAt(utc(request.getEndsAt()));
        exam.setDurationMinutes(request.getDurationMinutes()); exam.setMaxAttempts(request.getMaxAttempts());
        exam.setResultVisibility(request.getResultVisibility());
        exam.setShuffleQuestions(request.getShuffleQuestions()); exam.setShuffleOptions(request.getShuffleOptions());
        exam.getClasses().clear(); exam.getClasses().addAll(assigned);
    }

    private void validatePublish(Exam exam) {
        var teacher = users.findWithRolesById(exam.getTeacher().getId()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!teacher.isEnabled() || !ManagementMapper.hasRole(teacher, RoleName.TEACHER)) throw new AppException(ErrorCode.INVALID_TEACHER);
        boolean ready = exam.getStartsAt() != null && exam.getEndsAt() != null
                && exam.getEndsAt().isAfter(exam.getStartsAt()) && exam.getEndsAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))
                && exam.getDurationMinutes() > 0 && exam.getMaxAttempts() > 0 && !exam.getClasses().isEmpty()
                && exam.getClasses().stream().allMatch(item -> item.getTeacher().getId().equals(teacher.getId()));
        var links = examQuestions.findByExamIdOrderByDisplayOrderAsc(exam.getId());
        if (!ready || links.isEmpty()) throw new AppException(ErrorCode.EXAM_NOT_READY);
        var grouped = options.findByQuestionIdInOrderByDisplayOrderAsc(links.stream().map(item -> item.getQuestion().getId()).toList())
                .stream().collect(Collectors.groupingBy(item -> item.getQuestion().getId()));
        for (var link : links) {
            Question question = link.getQuestion();
            var choices = grouped.getOrDefault(question.getId(), List.of());
            boolean validChoices = question.getType() == QuestionType.ESSAY ? choices.isEmpty() : choices.size() >= 2
                    && choices.size() <= 10 && choices.stream().filter(QuestionOption::isCorrect).count() == 1
                    && choices.stream().noneMatch(item -> item.getContent().isBlank())
                    && choices.stream().map(item -> item.getContent().trim().toLowerCase(Locale.ROOT)).distinct().count() == choices.size();
            if (!question.isActive() || question.getContent().isBlank() || !question.getSubject().getId().equals(exam.getSubject().getId())
                    || link.getPoints() == null || link.getPoints().signum() <= 0 || !validChoices) {
                throw new AppException(ErrorCode.EXAM_NOT_READY);
            }
        }
    }

    private Exam getExam(UserResponse actor, Long id, boolean lock) {
        checkRole(actor);
        Exam exam = (lock ? exams.findForUpdate(id) : exams.findById(id))
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_NOT_FOUND));
        if (!isAdmin(actor) && !exam.getTeacher().getId().equals(actor.getId())) throw new AppException(ErrorCode.FORBIDDEN);
        return exam;
    }

    private ExamResponse response(Exam exam) {
        return ExamMapper.toResponse(exam, examQuestions.findByExamIdOrderByDisplayOrderAsc(exam.getId()).size());
    }

    private static void validateSchedule(OffsetDateTime start, OffsetDateTime end) {
        if ((start == null) != (end == null) || (start != null && !end.isAfter(start))) {
            throw new AppException(ErrorCode.INVALID_EXAM_SCHEDULE);
        }
    }
    private static LocalDateTime utc(OffsetDateTime time) { return time == null ? null : time.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
    private static void requireDraft(Exam exam) { if (exam.getStatus() != ExamStatus.DRAFT) throw new AppException(ErrorCode.EXAM_NOT_DRAFT); }
    private static boolean isAdmin(UserResponse actor) { return actor.getRoles().contains(RoleName.ADMIN); }
    private static void checkRole(UserResponse actor) { if (!isAdmin(actor) && !actor.getRoles().contains(RoleName.TEACHER)) throw new AppException(ErrorCode.FORBIDDEN); }
}
