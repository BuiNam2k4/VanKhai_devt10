package com.techbyte.ExamGuardBE.service.impl;

import com.techbyte.ExamGuardBE.common.ExamPaperFactory;
import com.techbyte.ExamGuardBE.controller.request.ExamQuestionsRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.*;
import com.techbyte.ExamGuardBE.exception.*;
import com.techbyte.ExamGuardBE.mapper.QuestionMapper;
import com.techbyte.ExamGuardBE.repository.*;
import com.techbyte.ExamGuardBE.service.ExamQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "EXAM-QUESTION-SERVICE")
public class ExamQuestionServiceImpl implements ExamQuestionService {
    private final ExamRepository exams;
    private final ExamQuestionRepository links;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final ExamPaperFactory papers;

    @Override
    @Transactional(readOnly = true)
    public ExamQuestionsResponse findAll(UserResponse actor, Long examId) { return response(getExam(actor, examId, false)); }

    @Override
    @Transactional
    public ExamQuestionsResponse replace(UserResponse actor, Long examId, ExamQuestionsRequest request) {
        Exam exam = getExam(actor, examId, true);
        if (exam.getStatus() != ExamStatus.DRAFT) throw new AppException(ErrorCode.EXAM_NOT_DRAFT);
        if (exams.hasSessions(examId)) throw new AppException(ErrorCode.RESOURCE_IN_USE);
        var existing = links.findByExamIdOrderByDisplayOrderAsc(examId);
        var byQuestion = existing.stream().collect(Collectors.toMap(link -> link.getQuestion().getId(), link -> link));
        Set<Long> ids = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var input : request.getQuestions()) {
            if (!ids.add(input.questionId())) throw new AppException(ErrorCode.INVALID_EXAM_QUESTIONS);
            total = total.add(input.points());
        }
        if (total.compareTo(ExamPaperFactory.MAX_POINTS) > 0) throw new AppException(ErrorCode.INVALID_EXAM_QUESTIONS);
        // Lock bank records in stable ID order, matching the bank's edit/delete locks.
        Map<Long, Question> sources = new HashMap<>();
        for (Long id : ids.stream().sorted().toList()) {
            var question = questions.findForUpdate(id).orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
            if (!isAdmin(actor) && !question.getCreatedBy().getId().equals(actor.getId()) && !byQuestion.containsKey(id)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
            sources.put(id, question);
        }
        var grouped = choices(ids);
        for (var question : sources.values()) ExamPaperFactory.validateQuestion(exam, question, grouped.getOrDefault(question.getId(), List.of()));
        // MySQL enforces unique (exam_id, display_order) for each statement, including swaps.
        int temporary = existing.stream().mapToInt(ExamQuestion::getDisplayOrder).max().orElse(0) + 1000;
        for (var link : existing) link.setDisplayOrder(++temporary);
        links.flush();
        links.deleteAll(existing.stream().filter(link -> !ids.contains(link.getQuestion().getId())).toList());
        links.flush();
        List<ExamQuestion> updated = new ArrayList<>(); int order = 0;
        for (var input : request.getQuestions()) {
            var link = byQuestion.getOrDefault(input.questionId(), new ExamQuestion());
            link.setExam(exam); link.setQuestion(sources.get(input.questionId())); link.setPoints(input.points()); link.setDisplayOrder(++order);
            updated.add(link);
        }
        links.saveAllAndFlush(updated);
        log.info("Replaced exam questions examId={} actorId={} count={}", examId, actor.getId(), updated.size());
        return response(exam);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamPaperResponse preview(UserResponse actor, Long examId) {
        var exam = getExam(actor, examId, false);
        var assigned = links.findByExamIdOrderByDisplayOrderAsc(examId);
        var paper = papers.create(exam, assigned, choices(assigned.stream().map(link -> link.getQuestion().getId()).toList()));
        List<ExamPaperResponse.Item> items = new ArrayList<>(); int order = 0;
        for (var question : paper.questions()) {
            List<ExamPaperResponse.Option> choices = new ArrayList<>(); int optionOrder = 0;
            for (var option : question.options()) choices.add(new ExamPaperResponse.Option(option.content(), ++optionOrder));
            items.add(new ExamPaperResponse.Item(question.source().getId(), question.content(), question.type(), question.points(), ++order, choices));
        }
        return new ExamPaperResponse(examId, exam.getTitle(), paper.totalPoints(), items);
    }

    private ExamQuestionsResponse response(Exam exam) {
        var assigned = links.findByExamIdOrderByDisplayOrderAsc(exam.getId());
        var grouped = choices(assigned.stream().map(link -> link.getQuestion().getId()).toList());
        return new ExamQuestionsResponse(exam.getId(), exam.getTitle(), exam.getStatus(), assigned.stream().map(ExamQuestion::getPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add), assigned.stream().map(link -> new ExamQuestionsResponse.Item(link.getId(),
                QuestionMapper.toResponse(link.getQuestion(), grouped.getOrDefault(link.getQuestion().getId(), List.of())), link.getPoints(), link.getDisplayOrder())).toList());
    }
    private Map<Long, List<QuestionOption>> choices(Collection<Long> ids) {
        return ids.isEmpty() ? Map.of() : options.findByQuestionIdInOrderByDisplayOrderAsc(ids).stream().collect(Collectors.groupingBy(option -> option.getQuestion().getId()));
    }
    private Exam getExam(UserResponse actor, Long id, boolean lock) {
        if (!isAdmin(actor) && !actor.getRoles().contains(RoleName.TEACHER)) throw new AppException(ErrorCode.FORBIDDEN);
        var exam = (lock ? exams.findForUpdate(id) : exams.findById(id)).orElseThrow(() -> new AppException(ErrorCode.EXAM_NOT_FOUND));
        if (!isAdmin(actor) && !exam.getTeacher().getId().equals(actor.getId())) throw new AppException(ErrorCode.FORBIDDEN);
        return exam;
    }
    private static boolean isAdmin(UserResponse actor) { return actor.getRoles().contains(RoleName.ADMIN); }
}
