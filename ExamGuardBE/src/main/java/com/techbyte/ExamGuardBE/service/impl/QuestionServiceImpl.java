package com.techbyte.ExamGuardBE.service.impl;

import com.techbyte.ExamGuardBE.common.PaginationUtils;
import com.techbyte.ExamGuardBE.controller.request.QuestionRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.*;
import com.techbyte.ExamGuardBE.exception.*;
import com.techbyte.ExamGuardBE.mapper.QuestionMapper;
import com.techbyte.ExamGuardBE.repository.*;
import com.techbyte.ExamGuardBE.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "QUESTION-SERVICE")
public class QuestionServiceImpl implements QuestionService {
    private static final Set<String> SORT_FIELDS = Set.of("id", "content", "difficulty", "type", "active", "createdAt", "updatedAt");
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final SubjectRepository subjects;
    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public QuestionPageResponse findAll(UserResponse actor, String keyword, Long subjectId, Difficulty difficulty,
                                        QuestionType type, Boolean active, String sortBy, int page, int size) {
        checkRole(actor);
        var pageable = PaginationUtils.pageable(page, size, sortBy, SORT_FIELDS);
        String search = keyword == null || keyword.isBlank() ? "" : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        var result = questions.search(search, subjectId, difficulty, type, active,
                isAdmin(actor) ? null : actor.getId(), pageable);
        var ids = result.getContent().stream().map(Question::getId).toList();
        var grouped = ids.isEmpty() ? Map.<Long, List<QuestionOption>>of() : options
                .findByQuestionIdInOrderByDisplayOrderAsc(ids).stream()
                .collect(Collectors.groupingBy(option -> option.getQuestion().getId()));
        QuestionPageResponse response = new QuestionPageResponse();
        response.setQuestions(result.getContent().stream().map(question -> QuestionMapper.toResponse(
                question, grouped.getOrDefault(question.getId(), List.of()))).toList());
        PaginationUtils.fill(response, page, size, result);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionResponse findById(UserResponse actor, Long id) {
        Question question = getQuestion(actor, id, false);
        return QuestionMapper.toResponse(question, options.findByQuestionIdOrderByDisplayOrderAsc(id));
    }

    @Override
    @Transactional
    public QuestionResponse save(UserResponse actor, QuestionRequest request) {
        checkRole(actor);
        validateOptions(request);
        if (request.getOptions().stream().anyMatch(option -> option.getId() != null)) {
            throw new AppException(ErrorCode.INVALID_OPTION_ID);
        }
        Question question = new Question();
        question.setCreatedBy(users.findById(actor.getId()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)));
        copy(request, question);
        questions.saveAndFlush(question);
        replaceOptions(question, request, List.of());
        log.info("Created question id={} authorId={}", question.getId(), actor.getId());
        return QuestionMapper.toResponse(question, options.findByQuestionIdOrderByDisplayOrderAsc(question.getId()));
    }

    @Override
    @Transactional
    public QuestionResponse update(UserResponse actor, Long id, QuestionRequest request) {
        Question question = getQuestion(actor, id, true);
        validateOptions(request);
        if ((!question.getSubject().getId().equals(request.getSubjectId()) || question.getType() != request.getType())
                && questions.isUsedInExam(id)) throw new AppException(ErrorCode.QUESTION_IN_EXAM);
        var existing = options.findByQuestionIdOrderByDisplayOrderAsc(id);
        copy(request, question);
        replaceOptions(question, request, existing);
        questions.saveAndFlush(question);
        log.info("Updated question id={} actorId={}", id, actor.getId());
        return QuestionMapper.toResponse(question, options.findByQuestionIdOrderByDisplayOrderAsc(id));
    }

    @Override
    @Transactional
    public void delete(UserResponse actor, Long id) {
        Question question = getQuestion(actor, id, true);
        if (questions.isUsedInExam(id)) throw new AppException(ErrorCode.QUESTION_IN_EXAM);
        options.deleteAll(options.findByQuestionIdOrderByDisplayOrderAsc(id));
        options.flush();
        questions.delete(question);
        questions.flush();
        log.info("Deleted question id={} actorId={}", id, actor.getId());
    }

    private Question getQuestion(UserResponse actor, Long id, boolean lock) {
        checkRole(actor);
        Question question = (lock ? questions.findForUpdate(id) : questions.findById(id))
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        if (!isAdmin(actor) && !question.getCreatedBy().getId().equals(actor.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return question;
    }

    private void copy(QuestionRequest request, Question question) {
        question.setSubject(subjects.findById(request.getSubjectId())
                .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND)));
        question.setContent(request.getContent().trim());
        question.setType(request.getType());
        question.setDifficulty(request.getDifficulty());
        question.setActive(request.getActive());
    }

    private static void validateOptions(QuestionRequest request) {
        var choices = request.getOptions();
        boolean valid = request.getType() == QuestionType.ESSAY ? choices.isEmpty() :
                choices.size() >= 2 && choices.size() <= 10
                        && choices.stream().filter(option -> Boolean.TRUE.equals(option.getCorrect())).count() == 1
                        && choices.stream().map(option -> option.getContent().trim().toLowerCase(Locale.ROOT))
                        .distinct().count() == choices.size();
        if (!valid) throw new AppException(ErrorCode.INVALID_QUESTION_OPTIONS);
    }

    private void replaceOptions(Question question, QuestionRequest request, List<QuestionOption> existing) {
        var byId = existing.stream().collect(Collectors.toMap(QuestionOption::getId, option -> option));
        Set<Long> kept = new HashSet<>();
        for (var input : request.getOptions()) {
            if (input.getId() != null && (!byId.containsKey(input.getId()) || !kept.add(input.getId()))) {
                throw new AppException(ErrorCode.INVALID_OPTION_ID);
            }
        }
        // Free the final order slots before a swap; MySQL checks unique constraints per statement.
        int temporaryOrder = existing.stream().mapToInt(QuestionOption::getDisplayOrder).max().orElse(0) + 10;
        for (var option : existing) option.setDisplayOrder(++temporaryOrder);
        options.flush();
        options.deleteAll(existing.stream().filter(option -> !kept.contains(option.getId())).toList());
        options.flush(); // Referenced historical options fail here and roll back the whole transaction.
        List<QuestionOption> updated = new ArrayList<>();
        int order = 0;
        for (var input : request.getOptions()) {
            QuestionOption option = input.getId() == null ? new QuestionOption() : byId.get(input.getId());
            option.setQuestion(question);
            option.setContent(input.getContent().trim());
            option.setCorrect(input.getCorrect());
            option.setDisplayOrder(++order);
            updated.add(option);
        }
        options.saveAllAndFlush(updated);
    }

    private static boolean isAdmin(UserResponse actor) { return actor.getRoles().contains(RoleName.ADMIN); }

    private static void checkRole(UserResponse actor) {
        if (!isAdmin(actor) && !actor.getRoles().contains(RoleName.TEACHER)) throw new AppException(ErrorCode.FORBIDDEN);
    }
}
