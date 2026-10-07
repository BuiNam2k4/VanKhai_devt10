package com.techbyte.ExamGuardBE.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_answers")
public class StudentAnswer extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_question_id", nullable = false)
    private SessionQuestion sessionQuestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "selected_option_id", nullable = true)
    private SessionQuestionOption selectedOption;

    @Column(name = "answer_text", nullable = true, columnDefinition = "TEXT")
    private String answerText;

    @Column(name = "awarded_points", nullable = true, precision = 8, scale = 2)
    private BigDecimal awardedPoints;

    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;

    @Setter(AccessLevel.NONE)
    @Version
    @Column(name = "version", nullable = false)
    private long version;

}
