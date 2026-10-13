package com.techbyte.ExamGuardBE.service.impl;

import com.techbyte.ExamGuardBE.common.ExamPaperFactory;
import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.*;
import com.techbyte.ExamGuardBE.exception.*;
import com.techbyte.ExamGuardBE.repository.*;
import com.techbyte.ExamGuardBE.service.ExamPaperSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamPaperSnapshotServiceImpl implements ExamPaperSnapshotService {
    private final ExamSessionRepository sessions;
    private final ExamQuestionRepository links;
    private final QuestionOptionRepository options;
    private final SessionQuestionRepository questions;
    private final SessionQuestionOptionRepository choices;
    private final ExamPaperFactory papers;
    private final QuestionRepository sources;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<SessionQuestion> createOrGet(Long sessionId) {
        var session = sessions.findForUpdate(sessionId).orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
        var existing = questions.findBySessionIdOrderByDisplayOrderAsc(sessionId);
        if (!existing.isEmpty()) return existing; // Resume uses saved order and never reads the bank again.
        if (session.getStatus() != SessionStatus.IN_PROGRESS || session.getExam().getStatus() != ExamStatus.PUBLISHED) {
            throw new AppException(ErrorCode.INVALID_SESSION_PAPER);
        }
        for (Long id : links.findQuestionIdsByExamId(session.getExam().getId())) sources.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        var assigned = links.findByExamIdOrderByDisplayOrderAsc(session.getExam().getId());
        var grouped = assigned.isEmpty() ? Map.<Long, List<QuestionOption>>of() : options.findForSnapshot(
                assigned.stream().map(link -> link.getQuestion().getId()).toList()).stream().collect(Collectors.groupingBy(option -> option.getQuestion().getId()));
        var paper = papers.create(session.getExam(), assigned, grouped);
        List<SessionQuestion> created = new ArrayList<>(); int order = 0;
        for (var input : paper.questions()) {
            var question = new SessionQuestion(); question.setSession(session); question.setExamId(session.getExam().getId());
            question.setExamQuestion(input.source()); question.setContent(input.content()); question.setType(input.type());
            question.setPoints(input.points()); question.setDisplayOrder(++order); questions.save(question); created.add(question);
            int optionOrder = 0;
            for (var option : input.options()) {
                var choice = new SessionQuestionOption(); choice.setSessionQuestion(question); choice.setSourceOption(option.source());
                choice.setContent(option.content()); choice.setCorrect(option.correct()); choice.setDisplayOrder(++optionOrder); choices.save(choice);
            }
        }
        choices.flush(); questions.flush(); session.setMaxScore(paper.totalPoints()); sessions.flush();
        return List.copyOf(created);
    }
}
