package com.techbyte.ExamGuardBE;

import com.techbyte.ExamGuardBE.common.ExamPaperFactory;
import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.QuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ExamPaperFactoryTests {
    @Test
    void shuffleFlagsIndependentlyChangeOnlyDisplayOrderAndPreserveAnswerIdentity() {
        var factory = new ExamPaperFactory(); var exam = new Exam(); var subject = new Subject(); ReflectionTestUtils.setField(subject, "id", 1L); exam.setSubject(subject);
        List<ExamQuestion> links = new ArrayList<>(); Map<Long, List<QuestionOption>> choices = new HashMap<>();
        for (long id = 1; id <= 3; id++) {
            var question = new Question(); ReflectionTestUtils.setField(question, "id", id); question.setSubject(subject); question.setContent("Q" + id);
            question.setType(QuestionType.SINGLE_CHOICE); question.setActive(true);
            var link = new ExamQuestion(); ReflectionTestUtils.setField(link, "id", id); link.setExam(exam); link.setQuestion(question); link.setPoints(BigDecimal.ONE); link.setDisplayOrder((int) id); links.add(link);
            List<QuestionOption> options = new ArrayList<>();
            for (int order = 1; order <= 2; order++) {
                var option = new QuestionOption(); ReflectionTestUtils.setField(option, "id", id * 10 + order); option.setQuestion(question); option.setContent("Q" + id + " option " + order);
                option.setCorrect(order == 1); option.setDisplayOrder(order); options.add(option);
            }
            choices.put(id, options);
        }
        for (boolean shuffleQuestions : List.of(false, true)) for (boolean shuffleOptions : List.of(false, true)) {
            exam.setShuffleQuestions(shuffleQuestions); exam.setShuffleOptions(shuffleOptions);
            var paper = factory.create(exam, links, choices, new Random() { @Override public int nextInt(int bound) { return 0; } });
            assertThat(paper.questions().stream().map(item -> item.source().getId())).containsExactlyElementsOf(shuffleQuestions ? List.of(2L, 3L, 1L) : List.of(1L, 2L, 3L));
            for (var question : paper.questions()) {
                long id = question.source().getQuestion().getId();
                assertThat(question.options().stream().map(option -> option.source().getId())).containsExactlyElementsOf(shuffleOptions ? List.of(id * 10 + 2, id * 10 + 1) : List.of(id * 10 + 1, id * 10 + 2));
                assertThat(question.options().stream().filter(ExamPaperFactory.PaperOption::correct).map(option -> option.source().getId())).containsExactly(id * 10 + 1);
            }
            assertThat(paper.totalPoints()).isEqualByComparingTo("3");
        }
        assertThat(links.stream().map(ExamQuestion::getId)).containsExactly(1L, 2L, 3L);
        assertThat(choices.get(1L).stream().map(QuestionOption::getId)).containsExactly(11L, 12L);
    }
}
