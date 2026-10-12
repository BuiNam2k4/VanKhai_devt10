package com.techbyte.ExamGuardBE.common;

import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.QuestionType;
import com.techbyte.ExamGuardBE.exception.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

@Component
public class ExamPaperFactory {
    public static final BigDecimal MAX_POINTS = new BigDecimal("999999.99");

    public Paper create(Exam exam, List<ExamQuestion> links, Map<Long, List<QuestionOption>> options) {
        return create(exam, links, options, ThreadLocalRandom.current());
    }

    public Paper create(Exam exam, List<ExamQuestion> links, Map<Long, List<QuestionOption>> options, RandomGenerator random) {
        if (links.isEmpty()) throw new AppException(ErrorCode.INVALID_EXAM_QUESTIONS);
        List<PaperQuestion> result = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        Set<Long> ids = new HashSet<>();
        for (var link : links.stream().sorted(Comparator.comparingInt(ExamQuestion::getDisplayOrder)).toList()) {
            var question = link.getQuestion();
            var choices = options.getOrDefault(question.getId(), List.of());
            validateQuestion(exam, question, choices);
            if (!ids.add(question.getId()) || link.getPoints() == null || link.getPoints().signum() <= 0
                    || link.getPoints().compareTo(MAX_POINTS) > 0 || link.getPoints().stripTrailingZeros().scale() > 2) {
                throw new AppException(ErrorCode.INVALID_EXAM_QUESTIONS);
            }
            total = total.add(link.getPoints());
            var copied = new ArrayList<>(choices.stream().sorted(Comparator.comparingInt(QuestionOption::getDisplayOrder))
                    .map(option -> new PaperOption(option, option.getContent(), option.isCorrect())).toList());
            if (exam.isShuffleOptions()) shuffle(copied, random);
            result.add(new PaperQuestion(link, question.getContent(), question.getType(), link.getPoints(), List.copyOf(copied)));
        }
        if (total.compareTo(MAX_POINTS) > 0) throw new AppException(ErrorCode.INVALID_EXAM_QUESTIONS);
        if (exam.isShuffleQuestions()) shuffle(result, random);
        return new Paper(total, List.copyOf(result));
    }

    public static void validateQuestion(Exam exam, Question question, List<QuestionOption> choices) {
        boolean valid = question.getType() == QuestionType.ESSAY ? choices.isEmpty() : choices.size() >= 2 && choices.size() <= 10
                && choices.stream().filter(QuestionOption::isCorrect).count() == 1
                && choices.stream().noneMatch(option -> option.getContent().isBlank())
                && choices.stream().map(option -> option.getContent().trim().toLowerCase(Locale.ROOT)).distinct().count() == choices.size();
        if (!question.isActive() || question.getContent().isBlank() || !question.getSubject().getId().equals(exam.getSubject().getId()) || !valid) {
            throw new AppException(ErrorCode.INVALID_EXAM_QUESTIONS);
        }
    }

    private static <T> void shuffle(List<T> items, RandomGenerator random) {
        for (int index = items.size() - 1; index > 0; index--) Collections.swap(items, index, random.nextInt(index + 1));
    }

    // Internal values include grading keys and source references solely for snapshot persistence.
    public record Paper(BigDecimal totalPoints, List<PaperQuestion> questions) {}
    public record PaperQuestion(ExamQuestion source, String content, QuestionType type, BigDecimal points, List<PaperOption> options) {}
    public record PaperOption(QuestionOption source, String content, boolean correct) {}
}
