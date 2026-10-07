-- Read-only checks. Each business/join table should have 24 demo records on a fresh database.
SELECT 'roles' AS table_name, COUNT(*) AS record_count FROM roles
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'user_roles', COUNT(*) FROM user_roles
UNION ALL SELECT 'subjects', COUNT(*) FROM subjects
UNION ALL SELECT 'classes', COUNT(*) FROM classes
UNION ALL SELECT 'class_students', COUNT(*) FROM class_students
UNION ALL SELECT 'questions', COUNT(*) FROM questions
UNION ALL SELECT 'question_options', COUNT(*) FROM question_options
UNION ALL SELECT 'exams', COUNT(*) FROM exams
UNION ALL SELECT 'exam_classes', COUNT(*) FROM exam_classes
UNION ALL SELECT 'exam_questions', COUNT(*) FROM exam_questions
UNION ALL SELECT 'exam_sessions', COUNT(*) FROM exam_sessions
UNION ALL SELECT 'session_questions', COUNT(*) FROM session_questions
UNION ALL SELECT 'session_question_options', COUNT(*) FROM session_question_options
UNION ALL SELECT 'student_answers', COUNT(*) FROM student_answers
UNION ALL SELECT 'exam_violation_logs', COUNT(*) FROM exam_violation_logs;

-- Every invalid_count should be zero. These checks go beyond foreign keys.
SELECT 'single_choice_options' AS check_name, COUNT(*) AS invalid_count
FROM (
    SELECT q.id FROM questions q
    LEFT JOIN question_options o ON o.question_id = q.id
    WHERE q.type = 'SINGLE_CHOICE'
    GROUP BY q.id HAVING COUNT(o.id) < 2 OR SUM(o.is_correct) <> 1
) invalid_options
UNION ALL
SELECT 'essay_options', COUNT(*) FROM questions q
JOIN question_options o ON o.question_id = q.id WHERE q.type = 'ESSAY'
UNION ALL
SELECT 'exam_subject', COUNT(*) FROM exam_questions eq
JOIN exams e ON e.id = eq.exam_id JOIN questions q ON q.id = eq.question_id
WHERE e.subject_id <> q.subject_id
UNION ALL
SELECT 'snapshot_source_option', COUNT(*) FROM session_question_options so
JOIN session_questions sq ON sq.id = so.session_question_id
JOIN exam_questions eq ON eq.id = sq.exam_question_id
JOIN question_options qo ON qo.id = so.source_option_id
WHERE eq.question_id <> qo.question_id
UNION ALL
SELECT 'answer_type', COUNT(*) FROM student_answers a
JOIN session_questions sq ON sq.id = a.session_question_id
WHERE (sq.type = 'ESSAY' AND (a.selected_option_id IS NOT NULL OR a.answer_text IS NULL))
   OR (sq.type = 'SINGLE_CHOICE' AND (a.selected_option_id IS NULL OR a.answer_text IS NOT NULL))
UNION ALL
SELECT 'answer_points', COUNT(*) FROM student_answers a
JOIN session_questions sq ON sq.id = a.session_question_id
WHERE a.awarded_points > sq.points
UNION ALL
SELECT 'session_score', COUNT(*) FROM exam_sessions es
WHERE es.status <> 'IN_PROGRESS' AND (es.score IS NULL OR es.score <>
    (SELECT SUM(a.awarded_points) FROM session_questions sq
     JOIN student_answers a ON a.session_question_id = sq.id WHERE sq.session_id = es.id))
UNION ALL
SELECT 'session_risk', COUNT(*) FROM exam_sessions es
WHERE es.risk_score <> (SELECT COALESCE(SUM(v.risk_weight), 0) FROM exam_violation_logs v WHERE v.session_id = es.id)
UNION ALL
SELECT 'answer_time', COUNT(*) FROM student_answers a
JOIN session_questions sq ON sq.id = a.session_question_id JOIN exam_sessions es ON es.id = sq.session_id
WHERE a.saved_at < es.started_at OR a.saved_at > COALESCE(es.submitted_at, es.expires_at)
UNION ALL
SELECT 'violation_time', COUNT(*) FROM exam_violation_logs v
JOIN exam_sessions es ON es.id = v.session_id
WHERE v.occurred_at < es.started_at OR v.occurred_at > COALESCE(es.submitted_at, es.expires_at)
UNION ALL
SELECT 'session_schedule', COUNT(*) FROM exam_sessions es JOIN exams e ON e.id = es.exam_id
WHERE es.started_at < e.starts_at OR es.started_at >= e.ends_at OR es.expires_at > e.ends_at
UNION ALL
SELECT 'student_eligibility', COUNT(*) FROM exam_sessions es JOIN exams e ON e.id = es.exam_id
WHERE es.attempt_number > e.max_attempts
   OR NOT EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = es.student_id AND r.name = 'STUDENT')
   OR NOT EXISTS (SELECT 1 FROM exam_classes ec JOIN class_students cs ON cs.class_id = ec.class_id WHERE ec.exam_id = e.id AND cs.student_id = es.student_id);

SELECT status, COUNT(*) AS session_count FROM exam_sessions GROUP BY status ORDER BY status;
SELECT username, full_name FROM users WHERE username LIKE 'demo_%' ORDER BY username;
