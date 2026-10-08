-- Development demo: 24 rows in each business/join table; roles retain 3 values.
-- Accounts: demo_admin01, demo_teacher01..03, demo_student01..20.
-- All demo passwords are Demo@123, stored as a verified BCrypt hash.
-- Existing rows are preserved. Run sequentially; natural keys avoid duplicate seed rows.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET time_zone = '+00:00';
SET @demo_anchor = UTC_TIMESTAMP(6);

CREATE TEMPORARY TABLE IF NOT EXISTS examguard_demo_items (
    n INT PRIMARY KEY,
    subject_name VARCHAR(150) NOT NULL,
    question_content TEXT NOT NULL,
    correct_option TEXT,
    wrong_option TEXT,
    essay_answer TEXT,
    full_name VARCHAR(150) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DELETE FROM examguard_demo_items;
INSERT INTO examguard_demo_items VALUES
    (1, 'Cơ sở dữ liệu', 'Khóa chính dùng để làm gì?', 'Định danh duy nhất mỗi bản ghi', 'Lưu mật khẩu người dùng', NULL, 'Quản trị viên mẫu'),
    (2, 'Lập trình Java', 'Giải thích tính đóng gói trong lập trình hướng đối tượng.', NULL, NULL, 'Đóng gói giới hạn việc truy cập trạng thái nội bộ và cung cấp phương thức để tương tác với đối tượng.', 'Nguyễn Minh An'),
    (3, 'Phát triển Web', 'HTML được dùng chủ yếu để làm gì?', 'Mô tả cấu trúc nội dung trang web', 'Quản lý giao dịch cơ sở dữ liệu', NULL, 'Trần Thu Hà'),
    (4, 'Hệ điều hành', 'Trình bày sự khác nhau giữa tiến trình và luồng.', NULL, NULL, 'Tiến trình có không gian địa chỉ riêng; các luồng trong cùng tiến trình chia sẻ nhiều tài nguyên và có ngăn xếp riêng.', 'Lê Quốc Huy'),
    (5, 'Mạng máy tính', 'DNS dùng để làm gì?', 'Phân giải tên miền thành địa chỉ IP', 'Chấm điểm bài thi', NULL, 'Phạm Văn Khải'),
    (6, 'Cấu trúc dữ liệu', 'So sánh ngăn xếp và hàng đợi, kèm ví dụ sử dụng.', NULL, NULL, 'Ngăn xếp theo LIFO, phù hợp thao tác hoàn tác; hàng đợi theo FIFO, phù hợp xử lý các yêu cầu theo thứ tự đến.', 'Nguyễn Hoàng Nam'),
    (7, 'Giải thuật', 'Tìm kiếm nhị phân yêu cầu dữ liệu như thế nào?', 'Dữ liệu đã được sắp xếp', 'Dữ liệu bắt buộc là chuỗi ký tự', NULL, 'Trần Minh Đức'),
    (8, 'An toàn thông tin', 'Vì sao nên lưu hash mật khẩu thay vì mật khẩu gốc?', NULL, NULL, 'Hash mật khẩu phù hợp giúp hạn chế lộ mật khẩu gốc khi dữ liệu bị truy cập trái phép; cần dùng salt và thuật toán chuyên dụng.', 'Lê Thị Mai'),
    (9, 'Kiểm thử phần mềm', 'Unit test thường tập trung kiểm tra phạm vi nào?', 'Một đơn vị mã với phạm vi nhỏ', 'Toàn bộ hệ thống triển khai trên Internet', NULL, 'Võ Anh Tuấn'),
    (10, 'Điện toán đám mây', 'Trình bày một lợi ích và một thách thức khi triển khai trên cloud.', NULL, NULL, 'Cloud hỗ trợ mở rộng tài nguyên theo nhu cầu; việc kiểm soát chi phí và cấu hình quyền truy cập cần được quản lý cẩn thận.', 'Đặng Ngọc Linh'),
    (11, 'Kiến trúc phần mềm', 'Tầng repository thường chịu trách nhiệm gì?', 'Truy cập và lưu trữ dữ liệu', 'Hiển thị nút trên giao diện', NULL, 'Bùi Quang Huy'),
    (12, 'Công nghệ phần mềm', 'Mô tả các bước chính của quy trình phát triển phần mềm.', NULL, NULL, 'Các bước thường gồm thu thập yêu cầu, phân tích, thiết kế, xây dựng, kiểm thử, triển khai và bảo trì.', 'Đỗ Thu Trang'),
    (13, 'DevOps', 'CI là viết tắt của khái niệm nào?', 'Continuous Integration', 'Class Initialization', NULL, 'Phan Nhật Minh'),
    (14, 'Lập trình Python', 'Giải thích sự khác nhau giữa list và tuple.', NULL, NULL, 'List có thể thay đổi các phần tử; tuple không cho phép thay đổi cấu trúc phần tử sau khi tạo.', 'Hoàng Gia Bảo'),
    (15, 'Lập trình JavaScript', 'const ngăn chặn thao tác nào với một biến?', 'Gán lại biến sang giá trị khác', 'Mọi thay đổi thuộc tính của object được tham chiếu', NULL, 'Ngô Thanh Tâm'),
    (16, 'Lập trình React', 'State và props có vai trò gì trong một component?', NULL, NULL, 'Props truyền dữ liệu từ bên ngoài; state lưu dữ liệu nội bộ thay đổi trong quá trình component hoạt động.', 'Dương Hải Yến'),
    (17, 'Spring Boot', 'Spring Data JPA hỗ trợ chủ yếu công việc nào?', 'Truy cập dữ liệu thông qua JPA', 'Vẽ biểu đồ trên trình duyệt', NULL, 'Vũ Đức Anh'),
    (18, 'Quản trị MySQL', 'Giải thích vai trò của transaction và rollback.', NULL, NULL, 'Transaction gom các thay đổi thành một đơn vị công việc; rollback hủy các thay đổi chưa commit khi có lỗi.', 'Mai Khánh Vy'),
    (19, 'Thiết kế API', 'GET thường được dùng cho mục đích nào?', 'Đọc tài nguyên', 'Thay thế mật khẩu trong mọi yêu cầu', NULL, 'Đinh Công Thành'),
    (20, 'Quản lý dự án', 'Nêu cách xử lý khi yêu cầu dự án thay đổi.', NULL, NULL, 'Cần phân tích tác động, trao đổi với bên liên quan, cập nhật phạm vi và kế hoạch trước khi thực hiện thay đổi.', 'Lý Phương Thảo'),
    (21, 'Git và quản lý phiên bản', 'Branch trong Git hỗ trợ việc gì?', 'Phát triển các dòng công việc riêng', 'Tự động bảo đảm mọi test đều đạt', NULL, 'Nguyễn Bảo Ngọc'),
    (22, 'Docker', 'Giải thích sự khác nhau giữa image và container.', NULL, NULL, 'Image chứa các lớp và cấu hình để tạo môi trường chạy; container là một thực thể được tạo từ image.', 'Trần Quốc Việt'),
    (23, 'Redis', 'Redis thường lưu dữ liệu chính ở đâu?', 'Trong bộ nhớ', 'Trong tệp DOCX', NULL, 'Lê Minh Châu'),
    (24, 'RabbitMQ', 'Mô tả lợi ích của việc xử lý sự kiện qua message queue.', NULL, NULL, 'Message queue giúp tách bên gửi khỏi bên xử lý và hỗ trợ xử lý bất đồng bộ; consumer cần xử lý retry và trùng message.', 'Phạm Anh Khoa');

START TRANSACTION;

INSERT INTO roles(name)
SELECT 'ADMIN' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');
INSERT INTO roles(name)
SELECT 'TEACHER' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'TEACHER');
INSERT INTO roles(name)
SELECT 'STUDENT' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'STUDENT');

INSERT INTO users(username, email, password_hash, full_name, enabled)
SELECT CASE WHEN n = 1 THEN 'demo_admin01'
            WHEN n <= 4 THEN CONCAT('demo_teacher', LPAD(n - 1, 2, '0'))
            ELSE CONCAT('demo_student', LPAD(n - 4, 2, '0')) END,
       CONCAT('demo', LPAD(n, 2, '0'), '@examguard.example'),
       '$2a$10$A8KSFbZpeCKCbcWiTc3pked4/d8Q9/Ta6BTjTD3m.v2P4RdUaKceC',
       full_name, TRUE
FROM examguard_demo_items i
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.username =
    CASE WHEN i.n = 1 THEN 'demo_admin01'
         WHEN i.n <= 4 THEN CONCAT('demo_teacher', LPAD(i.n - 1, 2, '0'))
         ELSE CONCAT('demo_student', LPAD(i.n - 4, 2, '0')) END);

INSERT INTO user_roles(user_id, role_id)
SELECT u.id, r.id
FROM examguard_demo_items i
JOIN users u ON u.username = CASE WHEN i.n = 1 THEN 'demo_admin01'
    WHEN i.n <= 4 THEN CONCAT('demo_teacher', LPAD(i.n - 1, 2, '0'))
    ELSE CONCAT('demo_student', LPAD(i.n - 4, 2, '0')) END
JOIN roles r ON r.name = CASE WHEN i.n = 1 THEN 'ADMIN' WHEN i.n <= 4 THEN 'TEACHER' ELSE 'STUDENT' END
WHERE NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO subjects(code, name, description)
SELECT CONCAT('DEMO-SUB-', LPAD(n, 2, '0')), subject_name,
       CONCAT('Môn học mẫu để thực hành và kiểm thử: ', subject_name)
FROM examguard_demo_items i
WHERE NOT EXISTS (SELECT 1 FROM subjects s WHERE s.code = CONCAT('DEMO-SUB-', LPAD(i.n, 2, '0')));

INSERT INTO classes(code, name, teacher_id)
SELECT CONCAT('DEMO-CLS-', LPAD(i.n, 2, '0')),
       CONCAT('Lớp thực hành ', LPAD(i.n, 2, '0'), ' - ', i.subject_name), u.id
FROM examguard_demo_items i
JOIN users u ON u.username = CONCAT('demo_teacher', LPAD(1 + MOD(i.n - 1, 3), 2, '0'))
WHERE NOT EXISTS (SELECT 1 FROM classes c WHERE c.code = CONCAT('DEMO-CLS-', LPAD(i.n, 2, '0')));

INSERT INTO class_students(class_id, student_id)
SELECT c.id, u.id
FROM examguard_demo_items i
JOIN classes c ON c.code = CONCAT('DEMO-CLS-', LPAD(i.n, 2, '0'))
JOIN users u ON u.username = CONCAT('demo_student', LPAD(1 + MOD(i.n - 1, 20), 2, '0'))
WHERE NOT EXISTS (SELECT 1 FROM class_students cs WHERE cs.class_id = c.id AND cs.student_id = u.id);

INSERT INTO questions(subject_id, created_by, content, type, difficulty, active)
SELECT s.id, u.id, i.question_content,
       IF(MOD(i.n, 2) = 1, 'SINGLE_CHOICE', 'ESSAY'),
       ELT(1 + MOD(i.n - 1, 3), 'EASY', 'MEDIUM', 'HARD'), TRUE
FROM examguard_demo_items i
JOIN subjects s ON s.code = CONCAT('DEMO-SUB-', LPAD(i.n, 2, '0'))
JOIN users u ON u.username = CONCAT('demo_teacher', LPAD(1 + MOD(i.n - 1, 3), 2, '0'))
WHERE NOT EXISTS (SELECT 1 FROM questions q WHERE q.subject_id = s.id AND q.content = i.question_content);

INSERT INTO question_options(question_id, content, is_correct, display_order)
SELECT q.id, IF(o.display_order = 1, i.correct_option, i.wrong_option), o.display_order = 1, o.display_order
FROM examguard_demo_items i
JOIN subjects s ON s.code = CONCAT('DEMO-SUB-', LPAD(i.n, 2, '0'))
JOIN questions q ON q.subject_id = s.id AND q.content = i.question_content
CROSS JOIN (SELECT 1 AS display_order UNION ALL SELECT 2) o
WHERE q.type = 'SINGLE_CHOICE'
AND NOT EXISTS (SELECT 1 FROM question_options qo WHERE qo.question_id = q.id AND qo.display_order = o.display_order);

INSERT INTO exams(subject_id, teacher_id, title, description, status, starts_at, ends_at,
                  duration_minutes, max_attempts, shuffle_questions, shuffle_options, result_visibility)
SELECT s.id, u.id, CONCAT('Bài kiểm tra mẫu ', LPAD(i.n, 2, '0'), ' - ', i.subject_name),
       'EXAMGUARD_DEMO_V1', IF(i.n <= 8, 'PUBLISHED', 'CLOSED'),
       IF(i.n <= 8, DATE_SUB(@demo_anchor, INTERVAL 1 HOUR), DATE_SUB(DATE_SUB(@demo_anchor, INTERVAL i.n DAY), INTERVAL 1 HOUR)),
       IF(i.n <= 8, DATE_ADD(@demo_anchor, INTERVAL 1 DAY), DATE_SUB(@demo_anchor, INTERVAL i.n DAY)),
       30, 3, TRUE, MOD(i.n, 4) = 3,
       ELT(1 + MOD(i.n - 1, 3), 'AFTER_SUBMISSION', 'AFTER_EXAM_END', 'HIDDEN')
FROM examguard_demo_items i
JOIN subjects s ON s.code = CONCAT('DEMO-SUB-', LPAD(i.n, 2, '0'))
JOIN users u ON u.username = CONCAT('demo_teacher', LPAD(1 + MOD(i.n - 1, 3), 2, '0'))
WHERE NOT EXISTS (SELECT 1 FROM exams e WHERE e.subject_id = s.id AND e.description = 'EXAMGUARD_DEMO_V1');

INSERT INTO exam_classes(exam_id, class_id)
SELECT e.id, c.id
FROM examguard_demo_items i
JOIN subjects s ON s.code = CONCAT('DEMO-SUB-', LPAD(i.n, 2, '0'))
JOIN exams e ON e.subject_id = s.id AND e.description = 'EXAMGUARD_DEMO_V1'
JOIN classes c ON c.code = CONCAT('DEMO-CLS-', LPAD(i.n, 2, '0'))
WHERE NOT EXISTS (SELECT 1 FROM exam_classes ec WHERE ec.exam_id = e.id AND ec.class_id = c.id);

INSERT INTO exam_questions(exam_id, question_id, points, display_order)
SELECT e.id, q.id, 10.00, 1
FROM examguard_demo_items i
JOIN subjects s ON s.code = CONCAT('DEMO-SUB-', LPAD(i.n, 2, '0'))
JOIN exams e ON e.subject_id = s.id AND e.description = 'EXAMGUARD_DEMO_V1'
JOIN questions q ON q.subject_id = s.id AND q.content = i.question_content
WHERE NOT EXISTS (SELECT 1 FROM exam_questions eq WHERE eq.exam_id = e.id AND eq.question_id = q.id);

INSERT INTO exam_sessions(exam_id, student_id, attempt_number, status, started_at, expires_at,
                          submitted_at, score, max_score, risk_score, device_id, ip_address, user_agent)
SELECT e.id, u.id, 1,
       CASE WHEN i.n <= 8 THEN 'IN_PROGRESS' WHEN i.n <= 16 THEN 'SUBMITTED' ELSE 'AUTO_SUBMITTED' END,
       IF(i.n <= 8, DATE_SUB(@demo_anchor, INTERVAL 10 MINUTE), DATE_SUB(e.ends_at, INTERVAL 30 MINUTE)),
       IF(i.n <= 8, DATE_ADD(@demo_anchor, INTERVAL 20 MINUTE), e.ends_at),
       CASE WHEN i.n <= 8 THEN NULL WHEN i.n <= 16 THEN DATE_SUB(e.ends_at, INTERVAL 5 MINUTE) ELSE e.ends_at END,
       CASE WHEN i.n <= 8 THEN NULL WHEN MOD(i.n, 2) = 0 THEN 8.00 WHEN MOD(i.n, 4) = 1 THEN 10.00 ELSE 0.00 END,
       10.00, ELT(1 + MOD(i.n - 1, 8), 5, 5, 10, 10, 3, 2, 0, 20),
       CONCAT('demo-device-', LPAD(i.n, 2, '0')), CONCAT('192.0.2.', i.n), 'ExamGuard Demo Browser'
FROM examguard_demo_items i
JOIN subjects s ON s.code = CONCAT('DEMO-SUB-', LPAD(i.n, 2, '0'))
JOIN exams e ON e.subject_id = s.id AND e.description = 'EXAMGUARD_DEMO_V1'
JOIN users u ON u.username = CONCAT('demo_student', LPAD(1 + MOD(i.n - 1, 20), 2, '0'))
WHERE NOT EXISTS (SELECT 1 FROM exam_sessions es WHERE es.exam_id = e.id AND es.student_id = u.id AND es.attempt_number = 1);

INSERT INTO session_questions(session_id, exam_question_id, exam_id, content, type, points, display_order)
SELECT es.id, eq.id, e.id, q.content, q.type, eq.points, eq.display_order
FROM examguard_demo_items i
JOIN subjects s ON s.code = CONCAT('DEMO-SUB-', LPAD(i.n, 2, '0'))
JOIN exams e ON e.subject_id = s.id AND e.description = 'EXAMGUARD_DEMO_V1'
JOIN exam_sessions es ON es.exam_id = e.id AND es.device_id = CONCAT('demo-device-', LPAD(i.n, 2, '0'))
JOIN questions q ON q.subject_id = s.id AND q.content = i.question_content
JOIN exam_questions eq ON eq.exam_id = e.id AND eq.question_id = q.id
WHERE NOT EXISTS (SELECT 1 FROM session_questions sq WHERE sq.session_id = es.id AND sq.exam_question_id = eq.id);

INSERT INTO session_question_options(session_question_id, source_option_id, content, is_correct, display_order)
SELECT sq.id, qo.id, qo.content, qo.is_correct,
       IF(e.shuffle_options, 3 - qo.display_order, qo.display_order)
FROM examguard_demo_items i
JOIN subjects s ON s.code = CONCAT('DEMO-SUB-', LPAD(i.n, 2, '0'))
JOIN exams e ON e.subject_id = s.id AND e.description = 'EXAMGUARD_DEMO_V1'
JOIN exam_sessions es ON es.exam_id = e.id AND es.device_id = CONCAT('demo-device-', LPAD(i.n, 2, '0'))
JOIN session_questions sq ON sq.session_id = es.id
JOIN exam_questions eq ON eq.id = sq.exam_question_id
JOIN question_options qo ON qo.question_id = eq.question_id
WHERE NOT EXISTS (SELECT 1 FROM session_question_options so WHERE so.session_question_id = sq.id AND so.source_option_id = qo.id);

INSERT INTO student_answers(session_question_id, selected_option_id, answer_text, awarded_points, saved_at)
SELECT sq.id, so.id, IF(sq.type = 'ESSAY', i.essay_answer, NULL),
       CASE WHEN es.status = 'IN_PROGRESS' THEN NULL WHEN sq.type = 'ESSAY' THEN 8.00 WHEN so.is_correct THEN sq.points ELSE 0.00 END,
       DATE_ADD(es.started_at, INTERVAL IF(es.status = 'IN_PROGRESS', 5, 20) MINUTE)
FROM examguard_demo_items i
JOIN subjects s ON s.code = CONCAT('DEMO-SUB-', LPAD(i.n, 2, '0'))
JOIN exams e ON e.subject_id = s.id AND e.description = 'EXAMGUARD_DEMO_V1'
JOIN exam_sessions es ON es.exam_id = e.id AND es.device_id = CONCAT('demo-device-', LPAD(i.n, 2, '0'))
JOIN session_questions sq ON sq.session_id = es.id
LEFT JOIN session_question_options so ON so.session_question_id = sq.id AND so.is_correct = (MOD(i.n, 4) = 1)
WHERE NOT EXISTS (SELECT 1 FROM student_answers a WHERE a.session_question_id = sq.id);

INSERT INTO exam_violation_logs(session_id, type, occurred_at, event_id, risk_weight, details)
SELECT es.id,
       ELT(1 + MOD(i.n - 1, 8), 'TAB_SWITCH', 'FULLSCREEN_EXIT', 'COPY', 'PASTE', 'RELOAD', 'DISCONNECT', 'RECONNECT', 'MULTI_DEVICE'),
       DATE_ADD(es.started_at, INTERVAL 3 MINUTE),
       CONCAT('00000000-0000-4000-8000-', LPAD(i.n, 12, '0')),
       ELT(1 + MOD(i.n - 1, 8), 5, 5, 10, 10, 3, 2, 0, 20),
       CONCAT('Sự kiện mẫu trong phiên thi ', LPAD(i.n, 2, '0'), '; dữ liệu dùng để kiểm thử giao diện lịch sử và điểm rủi ro.')
FROM examguard_demo_items i
JOIN subjects s ON s.code = CONCAT('DEMO-SUB-', LPAD(i.n, 2, '0'))
JOIN exams e ON e.subject_id = s.id AND e.description = 'EXAMGUARD_DEMO_V1'
JOIN exam_sessions es ON es.exam_id = e.id AND es.device_id = CONCAT('demo-device-', LPAD(i.n, 2, '0'))
WHERE NOT EXISTS (SELECT 1 FROM exam_violation_logs v WHERE v.session_id = es.id AND v.event_id = CONCAT('00000000-0000-4000-8000-', LPAD(i.n, 12, '0')));

COMMIT;
DROP TEMPORARY TABLE examguard_demo_items;
