package com.techbyte.ExamGuardBE.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.techbyte.ExamGuardBE.enums.SessionStatus;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exam_sessions")
public class ExamSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "submitted_at", nullable = true)
    private LocalDateTime submittedAt;

    @Column(name = "score", nullable = true, precision = 8, scale = 2)
    private BigDecimal score;

    @Column(name = "max_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "ip_address", nullable = true, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", nullable = true, length = 512)
    private String userAgent;

    @Setter(AccessLevel.NONE)
    @Version
    @Column(name = "version", nullable = false)
    private long version;

}
