-- MySQL >= 8.0.16. All application timestamps are stored in UTC.
-- Keep historical records: foreign keys deliberately do not cascade deletes.
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_roles_name UNIQUE (name),
    CONSTRAINT ck_role_name CHECK (name IN ('ADMIN', 'TEACHER', 'STUDENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS subjects (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_subjects_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS classes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    teacher_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_classes_code UNIQUE (code),
    CONSTRAINT fk_classes_teacher FOREIGN KEY (teacher_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS class_students (
    class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    PRIMARY KEY (class_id, student_id),
    CONSTRAINT fk_class_students_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_class_students_student FOREIGN KEY (student_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS questions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(20) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_questions_subject_difficulty (subject_id, difficulty, active),
    CONSTRAINT fk_questions_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_questions_author FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT ck_question_type CHECK (type IN ('SINGLE_CHOICE', 'ESSAY')),
    CONSTRAINT ck_question_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS question_options (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_question_options_order UNIQUE (question_id, display_order),
    CONSTRAINT fk_question_options_question FOREIGN KEY (question_id) REFERENCES questions(id),
    CONSTRAINT ck_question_options_order CHECK (display_order > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exams (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    starts_at DATETIME(6),
    ends_at DATETIME(6),
    duration_minutes INT NOT NULL,
    max_attempts INT NOT NULL DEFAULT 1,
    shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE,
    shuffle_options BOOLEAN NOT NULL DEFAULT FALSE,
    result_visibility VARCHAR(30) NOT NULL DEFAULT 'AFTER_EXAM_END',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_exams_status_time (status, starts_at, ends_at),
    CONSTRAINT fk_exams_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_exams_teacher FOREIGN KEY (teacher_id) REFERENCES users(id),
    CONSTRAINT ck_exam_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    CONSTRAINT ck_exam_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_exam_attempts CHECK (max_attempts > 0),
    CONSTRAINT ck_exam_schedule CHECK ((starts_at IS NULL AND ends_at IS NULL AND status = 'DRAFT') OR (starts_at IS NOT NULL AND ends_at IS NOT NULL AND ends_at > starts_at)),
    CONSTRAINT ck_exam_result_visibility CHECK (result_visibility IN ('AFTER_SUBMISSION', 'AFTER_EXAM_END', 'HIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exam_classes (
    exam_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    PRIMARY KEY (exam_id, class_id),
    CONSTRAINT fk_exam_classes_exam FOREIGN KEY (exam_id) REFERENCES exams(id),
    CONSTRAINT fk_exam_classes_class FOREIGN KEY (class_id) REFERENCES classes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exam_questions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    points DECIMAL(8,2) NOT NULL,
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_exam_questions_question UNIQUE (exam_id, question_id),
    CONSTRAINT uk_exam_questions_order UNIQUE (exam_id, display_order),
    CONSTRAINT uk_exam_questions_id_exam UNIQUE (id, exam_id),
    CONSTRAINT fk_exam_questions_exam FOREIGN KEY (exam_id) REFERENCES exams(id),
    CONSTRAINT fk_exam_questions_question FOREIGN KEY (question_id) REFERENCES questions(id),
    CONSTRAINT ck_exam_question_points CHECK (points > 0),
    CONSTRAINT ck_exam_question_order CHECK (display_order > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exam_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6),
    score DECIMAL(8,2),
    max_score DECIMAL(8,2) NOT NULL,
    risk_score INT NOT NULL DEFAULT 0,
    device_id VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    version BIGINT NOT NULL DEFAULT 0,
    -- MySQL allows multiple NULLs in a unique index, so completed attempts coexist.
    active_student_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'IN_PROGRESS' THEN student_id ELSE NULL END) STORED,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_sessions_attempt UNIQUE (exam_id, student_id, attempt_number),
    CONSTRAINT uk_sessions_active UNIQUE (exam_id, active_student_id),
    CONSTRAINT uk_sessions_id_exam UNIQUE (id, exam_id),
    INDEX idx_sessions_student_status (student_id, status),
    INDEX idx_sessions_exam_score (exam_id, score),
    INDEX idx_sessions_exam_submitted (exam_id, submitted_at),
    INDEX idx_sessions_exam_risk (exam_id, risk_score),
    INDEX idx_sessions_expiry (status, expires_at),
    CONSTRAINT fk_sessions_exam FOREIGN KEY (exam_id) REFERENCES exams(id),
    CONSTRAINT fk_sessions_student FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT ck_session_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'AUTO_SUBMITTED')),
    CONSTRAINT ck_session_attempt CHECK (attempt_number > 0),
    CONSTRAINT ck_session_deadline CHECK (expires_at > started_at),
    CONSTRAINT ck_session_submission CHECK ((status = 'IN_PROGRESS' AND submitted_at IS NULL) OR (status IN ('SUBMITTED', 'AUTO_SUBMITTED') AND submitted_at IS NOT NULL AND submitted_at >= started_at)),
    CONSTRAINT ck_session_score CHECK (max_score > 0 AND (score IS NULL OR (score >= 0 AND score <= max_score))),
    CONSTRAINT ck_session_risk CHECK (risk_score >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS session_questions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    exam_question_id BIGINT NOT NULL,
    exam_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(20) NOT NULL,
    points DECIMAL(8,2) NOT NULL,
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_session_questions_source UNIQUE (session_id, exam_question_id),
    CONSTRAINT uk_session_questions_order UNIQUE (session_id, display_order),
    CONSTRAINT fk_session_questions_session FOREIGN KEY (session_id, exam_id) REFERENCES exam_sessions(id, exam_id),
    CONSTRAINT fk_session_questions_exam_question FOREIGN KEY (exam_question_id, exam_id) REFERENCES exam_questions(id, exam_id),
    CONSTRAINT ck_session_question_type CHECK (type IN ('SINGLE_CHOICE', 'ESSAY')),
    CONSTRAINT ck_session_question_points CHECK (points > 0),
    CONSTRAINT ck_session_question_order CHECK (display_order > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS session_question_options (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_question_id BIGINT NOT NULL,
    source_option_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_session_options_source UNIQUE (session_question_id, source_option_id),
    CONSTRAINT uk_session_options_order UNIQUE (session_question_id, display_order),
    CONSTRAINT uk_session_options_id_question UNIQUE (id, session_question_id),
    CONSTRAINT fk_session_options_question FOREIGN KEY (session_question_id) REFERENCES session_questions(id),
    CONSTRAINT fk_session_options_source FOREIGN KEY (source_option_id) REFERENCES question_options(id),
    CONSTRAINT ck_session_option_order CHECK (display_order > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS student_answers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_question_id BIGINT NOT NULL,
    selected_option_id BIGINT,
    answer_text TEXT,
    awarded_points DECIMAL(8,2),
    saved_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_student_answers_question UNIQUE (session_question_id),
    CONSTRAINT fk_answers_question FOREIGN KEY (session_question_id) REFERENCES session_questions(id),
    -- A selected option must belong to this exact question in this exact attempt.
    CONSTRAINT fk_answers_option FOREIGN KEY (selected_option_id, session_question_id) REFERENCES session_question_options(id, session_question_id),
    CONSTRAINT ck_answer_format CHECK (selected_option_id IS NULL OR answer_text IS NULL),
    CONSTRAINT ck_answer_points CHECK (awarded_points IS NULL OR awarded_points >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exam_violation_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    risk_weight INT NOT NULL DEFAULT 0,
    details TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_violation_event UNIQUE (session_id, event_id),
    INDEX idx_violation_session_time (session_id, occurred_at),
    CONSTRAINT fk_violation_session FOREIGN KEY (session_id) REFERENCES exam_sessions(id),
    CONSTRAINT ck_violation_type CHECK (type IN ('TAB_SWITCH', 'FULLSCREEN_EXIT', 'COPY', 'PASTE', 'RELOAD', 'DISCONNECT', 'RECONNECT', 'MULTI_DEVICE')),
    CONSTRAINT ck_violation_weight CHECK (risk_weight >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
