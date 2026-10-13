# Task 9 — Danh sách bài thi sinh viên

## Phạm vi và quyền truy cập

Sinh viên có màn hình Bài thi của tôi để tra cứu đề được giao qua các lớp mình đang là thành viên. Chỉ hiển thị PUBLISHED và CLOSED; DRAFT bị ẩn kể cả khi biết ID. Truy cập ID của đề không được giao trả 404. Sinh viên bị gỡ khỏi lớp mất quyền đọc đề ngay; điều kiện được áp dụng trong truy vấn trước đếm/phân trang. Khi thuộc nhiều lớp cùng được giao một đề, đề chỉ xuất hiện một lần.

API lấy studentId từ principal đã xác thực. Admin/teacher không có role STUDENT không truy cập các endpoint này. Danh mục môn cũng chỉ chứa môn có đề nhìn thấy được. Không trả nội dung câu hỏi, lựa chọn, đáp án đúng, điểm, lịch sử người khác hoặc thông tin thiết bị. ResultVisibility chỉ là tên chính sách; Task 9 chưa có API xem điểm/kết quả.

## API

| Method | Endpoint | Response data |
| --- | --- | --- |
| GET | /api/student/exams | pageNumber, pageSize, totalPages, totalElements, serverTime, exams |
| GET | /api/student/exams/{id} | Thông tin bài và điều kiện tham gia hiện tại |
| GET | /api/student/subjects | Danh mục môn được giao, theo hợp đồng phân trang |

Gửi `Authorization: Bearer <token>`. Query danh sách: keyword (tiêu đề/mô tả/tên và mã môn), subjectId, status=PUBLISHED/CLOSED, window=UPCOMING/OPEN/ENDED, sortBy, page, size. Gửi status=DRAFT luôn trả danh sách rỗng. Mọi bộ lọc áp dụng trong database trước phân trang.

Giữ hợp đồng của code base: mặc định page=0, size=20; page 0 và 1 cùng là trang đầu, page dương chuyển thành page−1. Size 1…100. Sort `field:asc|desc` với whitelist id, title, startsAt, endsAt, durationMinutes, createdAt; thêm id để ổn định thứ tự. UI dùng sáu đề mỗi trang, tải đủ danh mục môn qua các trang tối đa 100 bản ghi.

Ví dụ `/api/student/exams?page=2&size=6&sortBy=title:asc&status=PUBLISHED&window=OPEN`.

Các trường quan trọng của từng đề: id/title/description, thông tin môn/giảng viên, status, startsAt/endsAt, durationMinutes, maxAttempts, questionCount, attemptsUsed, attemptsRemaining, participationStatus/participationMessage, canStart/canResume, activeSessionId/activeSessionExpiresAt (chỉ phiên của tài khoản hiện tại), resultVisibility, serverTime.

## Điều kiện tham gia

Thời điểm lưu và trả theo UTC (`Z`), giao diện chuyển sang múi giờ thiết bị. Mỗi response dùng một thời điểm máy chủ nhất quán; trình duyệt không quyết định quyền tham gia. Trong cửa sổ thi, điều kiện mở là startsAt ≤ now < endsAt.

| participationStatus | Ý nghĩa |
| --- | --- |
| AVAILABLE | PUBLISHED, trong lịch mở, chưa có phiên đang làm và còn lượt; canStart=true |
| UPCOMING | Chưa đến startsAt |
| IN_PROGRESS | Có phiên hiện tại còn hạn; canResume=true, canStart=false |
| SESSION_EXPIRED | Phiên IN_PROGRESS đã hết giờ; chờ chốt phiên trước khi cấp lượt mới |
| ATTEMPTS_EXHAUSTED | Số phiên đã tạo bằng hoặc vượt maxAttempts |
| EXPIRED | Đã đến hoặc qua endsAt |
| CLOSED | Đề đóng và không có phiên còn hạn để tiếp tục |
| UNAVAILABLE | Cấu hình không đáp ứng điều kiện cơ bản |

attemptsUsed đếm tất cả trạng thái phiên của chính sinh viên, gồm IN_PROGRESS, SUBMITTED và AUTO_SUBMITTED. attemptsRemaining=max(0,maxAttempts−attemptsUsed). Một phiên còn hạn được ưu tiên hiển thị IN_PROGRESS kể cả khi đã dùng lượt cuối. Đóng đề chặn lượt mới; phiên đã tồn tại còn hạn vẫn có thể tiếp tục. Tại đúng thời điểm hết hạn phiên, canResume=false. Task 9 chỉ đọc dữ liệu, không tự nộp/chốt phiên hết giờ.

window lọc lịch mở/đóng, độc lập trạng thái đề và số lượt. Do đó đề còn trong lịch vẫn có thể CLOSED hoặc hết lượt; xem participationStatus để biết lý do cụ thể. Số phiên, phiên đang làm và số câu được truy vấn theo nhóm ID của trang, không truy vấn riêng cho từng thẻ bài.

## Giao diện và cách thử

1. Đăng nhập `demo_student01` hoặc các tài khoản demo_student02…20, mật khẩu `Demo@123`.
2. Tìm, lọc môn/trạng thái/lịch và chuyển trang. Nếu chưa có đề phù hợp, giao diện giải thích việc chỉ hiển thị đề đã công bố/đóng thuộc lớp được giao.
3. Chọn Xem bài thi để lấy lại thông tin và điều kiện hiện tại từ máy chủ. Màn hình danh sách tự làm mới mỗi phút, khi quay lại cửa sổ hoặc bấm Làm mới.
4. Muốn thử bộ đề 10 câu: giảng viên chọn Đề thực hành 10 câu, đặt lịch còn hiệu lực và Công bố. Sinh viên thuộc lớp được giao mới thấy đề. Các đề demo cũ có thể đã hết lịch hoặc có phiên hết hạn từ ngày nhập dữ liệu.

Task 10 đã triển khai endpoint bắt đầu/tiếp tục phiên, kiểm tra lại điều kiện và cạnh tranh cấp lượt, rồi gọi dịch vụ ảnh chụp Task 8. Các cờ canStart/canResume ở Task 9 là thông tin tra cứu tại thời điểm response, không phải giấy phép bỏ qua kiểm tra ở bước tạo phiên. Xem [hướng dẫn Task 10](exam-session-timer.md).

## Kiểm chứng

84 kiểm thử backend đạt trên H2 và MySQL Docker, gồm phạm vi lớp trước phân trang, tránh đếm trùng, ẩn nháp/đề khác lớp, bộ lọc/danh mục môn, lượt riêng từng sinh viên, trạng thái phiên, không lộ dữ liệu chấm điểm, mất quyền khi gỡ lớp và validation. Kiểm thử đơn vị kiểm tra chính xác thời điểm mở/đóng, hết hạn phiên và lượt cuối. Một ca hồi quy đọc/ghi timestamp bằng SQL trực tiếp kiểm tra giờ UTC trong cột DATETIME khớp response, không bị cộng/trừ múi giờ JVM.

Kết nối MySQL đặt connectionTimeZone=UTC và forceConnectionTimeZoneToSession=true; Hibernate dùng java_time_use_direct_jdbc để đọc/ghi LocalDateTime trực tiếp, tránh chuyển vòng qua java.sql.Timestamp làm lệch giờ. Cấu hình không sửa dữ liệu lịch sử trong database.

Tham khảo cấu hình: [MySQL Connector/J về xử lý thời gian](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html) và [Hibernate MappingSettings về JAVA_TIME_USE_DIRECT_JDBC](https://docs.jboss.org/hibernate/orm/6.6/javadocs/org/hibernate/cfg/MappingSettings.html).

```powershell
cd ExamGuardBE
$env:EXAMGUARD_MYSQL_TEST='true'
.\mvnw.cmd test '-Dspring.jpa.show-sql=false'
```

Frontend: npm run lint, npm run build. Kiểm tra Edge với MySQL thật dùng fixture có mã riêng, gồm danh sách chín đề/phân trang, lọc lịch/trạng thái, chi tiết hết lượt/lượt đang làm, trạng thái rỗng và màn hình di động. Fixture được dọn sau kiểm tra.
