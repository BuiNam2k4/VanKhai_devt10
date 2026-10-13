package com.techbyte.ExamGuardBE.service.impl;

import com.techbyte.ExamGuardBE.common.*;
import com.techbyte.ExamGuardBE.controller.request.StartExamSessionRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.*;
import com.techbyte.ExamGuardBE.exception.*;
import com.techbyte.ExamGuardBE.repository.*;
import com.techbyte.ExamGuardBE.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "EXAM-SESSION-SERVICE")
public class ExamSessionServiceImpl implements ExamSessionService {
    private final ExamRepository exams;
    private final ExamSessionRepository sessions;
    private final UserRepository users;
    private final ExamQuestionRepository links;
    private final QuestionRepository sources;
    private final QuestionOptionRepository sourceOptions;
    private final SessionQuestionRepository questions;
    private final SessionQuestionOptionRepository options;
    private final StudentAnswerRepository answers;
    private final ExamPaperFactory papers;
    private final ExamPaperSnapshotService snapshots;
    private final Clock clock;

    @Override
    @Transactional
    public StartResult start(UserResponse actor, Long examId, StartExamSessionRequest request, String ipAddress, String userAgent) {
        checkRole(actor);
        // The same lock is used by exam edits/closing and every attempt allocation.
        var exam = exams.findForUpdate(examId).orElseThrow(() -> new AppException(ErrorCode.EXAM_NOT_FOUND));
        if (exam.getStatus() == ExamStatus.DRAFT || !exams.isAssignedToStudent(examId, actor.getId())) throw new AppException(ErrorCode.EXAM_NOT_FOUND);
        var attempts = sessions.findByExamIdAndStudentIdOrderByAttemptNumberAsc(examId, actor.getId());
        var active = attempts.stream().filter(item -> item.getStatus() == SessionStatus.IN_PROGRESS).findFirst();
        if (active.isPresent()) {
            var session = sessions.findForUpdate(active.get().getId()).orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
            checkDevice(session, request.deviceId());
            return new StartResult(response(session), false); // Expired attempts are returned as expired, never extended or duplicated.
        }
        var now = now();
        if (exam.getStatus() != ExamStatus.PUBLISHED) throw new AppException(ErrorCode.EXAM_CLOSED);
        if (exam.getStartsAt() == null || exam.getEndsAt() == null || now.isBefore(exam.getStartsAt()) || !now.isBefore(exam.getEndsAt())
                || exam.getDurationMinutes() <= 0 || exam.getMaxAttempts() <= 0) throw new AppException(ErrorCode.EXAM_NOT_OPEN);
        int number = attempts.stream().mapToInt(ExamSession::getAttemptNumber).max().orElse(0);
        if (number >= exam.getMaxAttempts() || attempts.size() >= exam.getMaxAttempts()) throw new AppException(ErrorCode.ATTEMPTS_EXHAUSTED);
        // Read locked current bank data; this keeps content and grading keys consistent during concurrent bank edits.
        for (Long id : links.findQuestionIdsByExamId(examId)) sources.findForUpdate(id).orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        var assigned = links.findByExamIdOrderByDisplayOrderAsc(examId);
        var grouped = assigned.isEmpty() ? Map.<Long, List<QuestionOption>>of() : sourceOptions.findForSnapshot(
                assigned.stream().map(item -> item.getQuestion().getId()).toList()).stream().collect(Collectors.groupingBy(item -> item.getQuestion().getId()));
        var paper = papers.create(exam, assigned, grouped);
        var session = new ExamSession(); session.setExam(exam);
        session.setStudent(users.findById(actor.getId()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)));
        session.setAttemptNumber(number + 1); session.setStartedAt(now);
        session.setExpiresAt(ExamSessionTime.deadline(now, exam.getDurationMinutes(), exam.getEndsAt()));
        session.setMaxScore(paper.totalPoints()); session.setDeviceId(request.deviceId());
        session.setIpAddress(shorten(ipAddress, 45)); session.setUserAgent(shorten(userAgent, 512));
        sessions.saveAndFlush(session);
        snapshots.createOrGet(session.getId()); // Mandatory propagation: any copy failure rolls back the entire attempt.
        log.info("Started session id={} examId={} studentId={} attempt={}", session.getId(), examId, actor.getId(), session.getAttemptNumber());
        return new StartResult(response(session), true);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamSessionResponse restore(UserResponse actor, Long sessionId, String deviceId) {
        checkRole(actor);
        var session = sessions.findOwned(sessionId, actor.getId()).orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
        if (!exams.isAssignedToStudent(session.getExam().getId(), actor.getId())) throw new AppException(ErrorCode.SESSION_NOT_FOUND);
        checkDevice(session, deviceId); return response(session);
    }

    private ExamSessionResponse response(ExamSession session) {
        var saved = questions.findBySessionIdOrderByDisplayOrderAsc(session.getId());
        if (saved.isEmpty()) throw new AppException(ErrorCode.SESSION_PAPER_MISSING);
        var grouped = options.findBySessionQuestionIdInOrderByDisplayOrderAsc(saved.stream().map(SessionQuestion::getId).toList())
                .stream().collect(Collectors.groupingBy(item -> item.getSessionQuestion().getId()));
        var now = now(); boolean expired = !now.isBefore(session.getExpiresAt());
        boolean canContinue = session.getStatus() == SessionStatus.IN_PROGRESS && !expired;
        return new ExamSessionResponse(session.getId(), session.getExam().getId(), session.getExam().getTitle(), session.getExam().getSubject().getName(),
                session.getAttemptNumber(), session.getStatus(), utc(session.getStartedAt()), utc(session.getExpiresAt()), utc(now),
                canContinue ? ExamSessionTime.remainingSeconds(now, session.getExpiresAt()) : 0, expired, canContinue, session.getMaxScore(),
                saved.stream().map(question -> new ExamSessionResponse.Question(question.getId(), question.getContent(), question.getType(), question.getPoints(), question.getDisplayOrder(),
                    grouped.getOrDefault(question.getId(), List.of()).stream().map(option -> new ExamSessionResponse.Option(option.getId(), option.getContent(), option.getDisplayOrder())).toList())).toList(),
                answers.findForSession(session.getId()).stream().map(StudentAnswersResponse::from).toList());
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS); }
    private static OffsetDateTime utc(LocalDateTime time) { return time.atOffset(ZoneOffset.UTC); }
    private static void checkRole(UserResponse actor) { if (!actor.getRoles().contains(RoleName.STUDENT)) throw new AppException(ErrorCode.FORBIDDEN); }
    private static void checkDevice(ExamSession session, String deviceId) { if (!session.getDeviceId().equals(deviceId)) throw new AppException(ErrorCode.SESSION_DEVICE_CONFLICT); }
    private static String shorten(String value, int limit) { return value == null ? null : value.substring(0, Math.min(value.length(), limit)); }
}
