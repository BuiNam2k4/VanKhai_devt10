package com.techbyte.ExamGuardBE.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import com.techbyte.ExamGuardBE.enums.ViolationType;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exam_violation_logs")
public class ExamViolationLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ViolationType type;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "risk_weight", nullable = false)
    private int riskWeight;

    @Column(name = "details", nullable = true, columnDefinition = "TEXT")
    private String details;

}
