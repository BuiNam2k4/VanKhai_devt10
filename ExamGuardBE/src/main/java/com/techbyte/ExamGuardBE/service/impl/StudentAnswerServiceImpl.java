package com.techbyte.ExamGuardBE.service.impl;

import com.techbyte.ExamGuardBE.controller.request.SaveStudentAnswersRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.*;
import com.techbyte.ExamGuardBE.exception.*;
import com.techbyte.ExamGuardBE.repository.*;
import com.techbyte.ExamGuardBE.service.StudentAnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentAnswerServiceImpl implements StudentAnswerService {
    private final ExamSessionRepository sessions;
    private final ExamRepository exams;
    private final SessionQuestionRepository questions;
    private final SessionQuestionOptionRepository options;
    private final StudentAnswerRepository answers;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public StudentAnswersResponse findAll(UserResponse actor, Long sessionId, String deviceId) {
        var session = owned(actor, sessionId, deviceId, false);
        if (questions.findBySessionIdOrderByDisplayOrderAsc(sessionId).isEmpty()) throw new AppException(ErrorCode.SESSION_PAPER_MISSING);
        return response(session);
    }

    @Override
    @Transactional
    public StudentAnswersResponse save(UserResponse actor, Long sessionId, SaveStudentAnswersRequest request) {
        // Lock the owned session directly, without first loading a potentially stale managed entity.
        // This serializes all answer batches and future submission on the same attempt.
        var session = owned(actor, sessionId, request.deviceId(), true);
        requireWritable(session);
        var paper = questions.findBySessionIdOrderByDisplayOrderAsc(sessionId);
        if (paper.isEmpty()) throw new AppException(ErrorCode.SESSION_PAPER_MISSING);
        var byId = paper.stream().collect(Collectors.toMap(SessionQuestion::getId, item -> item));
        var choices = options.findBySessionQuestionIdInOrderByDisplayOrderAsc(byId.keySet()).stream()
                .collect(Collectors.toMap(SessionQuestionOption::getId, item -> item));
        var saved = answers.findForSession(sessionId).stream().collect(Collectors.toMap(item -> item.getSessionQuestion().getId(), item -> item));
        var seen = new HashSet<Long>();
        for (var item : request.answers()) {
            var question = byId.get(item.sessionQuestionId());
            if (question == null || !seen.add(item.sessionQuestionId())) throw new AppException(ErrorCode.INVALID_STUDENT_ANSWERS);
            if (question.getType() == QuestionType.SINGLE_CHOICE) {
                if (item.answerText() != null) throw new AppException(ErrorCode.INVALID_STUDENT_ANSWERS);
                if (item.selectedOptionId() != null && (!choices.containsKey(item.selectedOptionId())
                        || !choices.get(item.selectedOptionId()).getSessionQuestion().getId().equals(question.getId()))) {
                    throw new AppException(ErrorCode.INVALID_STUDENT_ANSWERS);
                }
            } else if (item.selectedOptionId() != null) throw new AppException(ErrorCode.INVALID_STUDENT_ANSWERS);
            var previous = saved.get(question.getId());
            // Identical retransmissions are safe even when their acknowledgement was lost.
            if (previous == null ? item.version() != null
                    : !sameValue(previous, item) && !Objects.equals(item.version(), previous.getVersion())) {
                throw new AppException(ErrorCode.ANSWER_VERSION_CONFLICT);
            }
        }
        requireWritable(session);
        var now = now();
        for (var item : request.answers()) {
            var answer = saved.get(item.sessionQuestionId());
            if (answer != null && sameValue(answer, item)) continue;
            if (answer == null) { answer = new StudentAnswer(); answer.setSessionQuestion(byId.get(item.sessionQuestionId())); }
            answer.setSelectedOption(item.selectedOptionId() == null ? null : choices.get(item.selectedOptionId()));
            answer.setAnswerText(normalizeText(item.answerText()));
            answer.setSavedAt(now);
            answers.save(answer);
        }
        answers.flush();
        return response(session);
    }

    private ExamSession owned(UserResponse actor, Long id, String deviceId, boolean lock) {
        if (!actor.getRoles().contains(RoleName.STUDENT)) throw new AppException(ErrorCode.FORBIDDEN);
        var session = (lock ? sessions.findOwnedForUpdate(id, actor.getId()) : sessions.findOwned(id, actor.getId()))
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
        if (!exams.isAssignedToStudent(session.getExam().getId(), actor.getId())) throw new AppException(ErrorCode.SESSION_NOT_FOUND);
        if (!session.getDeviceId().equals(deviceId)) throw new AppException(ErrorCode.SESSION_DEVICE_CONFLICT);
        return session;
    }
    private void requireWritable(ExamSession session) {
        if (session.getStatus() != SessionStatus.IN_PROGRESS || !now().isBefore(session.getExpiresAt())) throw new AppException(ErrorCode.SESSION_NOT_WRITABLE);
    }
    private StudentAnswersResponse response(ExamSession session) {
        var now = now();
        return new StudentAnswersResponse(session.getId(), now.atOffset(ZoneOffset.UTC), session.getExpiresAt().atOffset(ZoneOffset.UTC),
                session.getStatus() == SessionStatus.IN_PROGRESS && now.isBefore(session.getExpiresAt()),
                answers.findForSession(session.getId()).stream().map(StudentAnswersResponse::from).toList());
    }
    private boolean sameValue(StudentAnswer saved, SaveStudentAnswersRequest.Answer item) {
        return Objects.equals(saved.getSelectedOption() == null ? null : saved.getSelectedOption().getId(), item.selectedOptionId())
                && Objects.equals(saved.getAnswerText(), normalizeText(item.answerText()));
    }
    private static String normalizeText(String text) { return text == null || text.isBlank() ? null : text; }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS); }
}
