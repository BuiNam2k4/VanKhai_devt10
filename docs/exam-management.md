# Task 7 — Quản lý và cấu hình bài thi

## Phạm vi

Backend giữ cấu trúc Controller → Service/ServiceImpl → Repository, DTO request/response, mapper và ApiResponse của các task trước. Frontend có tab **Quản lý bài thi** trong màn hình giảng viên và admin. Không cần thay schema database Task 4.

Giảng viên chỉ thao tác bài thi do mình phụ trách. Teacher ID được lấy từ tài khoản đã xác thực; gửi teacherId của người khác bị từ chối. Admin thao tác tất cả bài thi và chọn giảng viên có role TEACHER, đang hoạt động. Sinh viên không được truy cập các API quản lý này.

## API

Hai namespace `/api/teacher/exams` và `/api/admin/exams` dùng các endpoint sau. Thêm header `Authorization: Bearer <token>`.

| Method | Đường dẫn sau namespace | Chức năng |
| --- | --- | --- |
| GET | / | Tìm kiếm, lọc, sắp xếp, phân trang |
| GET | /{id} | Xem chi tiết cấu hình |
| POST | / | Tạo bài nháp, HTTP 201 |
| PUT | /{id} | Cập nhật toàn bộ cấu hình bài nháp |
| DELETE | /{id} | Xóa bài nháp và liên kết của đề |
| POST | /{id}/publish | Công bố bài thi |
| POST | /{id}/close | Đóng bài thi đã công bố |

`GET /api/teacher/classes` trả danh sách lớp giảng viên phụ trách để chọn lớp được giao. Admin sử dụng danh mục lớp/người dùng hiện có.

### Phân trang

Query gồm `keyword` (tiêu đề/mô tả), `subjectId`, `status`, `teacherId` (admin), `page`, `size`, `sortBy`. Teacher luôn bị giới hạn về tài khoản hiện tại kể cả khi gửi teacherId khác.

Ví dụ: `/api/teacher/exams?page=2&size=10&sortBy=title:asc&status=DRAFT`.

Giữ hợp đồng phân trang từ Task 5/6: mặc định page=0, size=20; page 0 và 1 cùng trỏ trang đầu, page dương được chuyển thành page−1 khi truy vấn. Size từ 1 đến 100. Response data có `pageNumber`, `pageSize`, `totalPages`, `totalElements`, `exams`. Sort whitelist: id, title, status, startsAt, endsAt, durationMinutes, maxAttempts, createdAt, updatedAt; dùng `field:asc` hoặc `field:desc`.

Collection lớp được tải sau khi phân trang; số câu hỏi được đếm theo nhóm ID của trang, tránh phân trang trong bộ nhớ khi fetch join collection.

### Request tạo/cập nhật

```json
{
  "subjectId": 1,
  "teacherId": 2,
  "title": "Kiểm tra giữa kỳ",
  "description": "Thời lượng 45 phút mỗi lượt",
  "startsAt": "2099-05-01T09:00:00+07:00",
  "endsAt": "2099-05-01T11:00:00+07:00",
  "durationMinutes": 45,
  "maxAttempts": 1,
  "resultVisibility": "AFTER_EXAM_END",
  "shuffleQuestions": false,
  "shuffleOptions": false,
  "classIds": [1, 2]
}
```

ID trong ví dụ cần thay bằng dữ liệu thật. Teacher có thể bỏ teacherId, admin phải gửi. Title không được trống, tối đa 200 ký tự; description tối đa 10.000 ký tự. Duration/maxAttempts là số nguyên dương. Class IDs phải tồn tại và thuộc giảng viên của bài thi. Nháp được để trống cả hai thời điểm và chưa giao lớp; lịch chỉ có một thời điểm hoặc thời gian đóng không sau mở bị HTTP 400.

Thời điểm phải có offset hoặc Z. Backend chuyển về UTC khi lưu và trả về UTC (`Z`); frontend hiển thị/nhập theo múi giờ thiết bị và chuyển sang ISO UTC khi gửi. `resultVisibility` có ba giá trị: AFTER_SUBMISSION, AFTER_EXAM_END, HIDDEN. Task này lưu chính sách; API lượt thi/kết quả ở task sau sẽ áp dụng giới hạn thời gian, số lượt và quyền xem kết quả.

### Trạng thái và công bố

Chỉ có luồng **DRAFT → PUBLISHED → CLOSED**. Tạo luôn DRAFT, không nhận trạng thái tùy ý từ request. Chỉ sửa hoặc xóa DRAFT. Không công bố lại hay mở lại CLOSED. Backend khóa bản ghi khi cập nhật/chuyển trạng thái/xóa để tuần tự hóa các thao tác cùng bài thi.

Trước khi công bố, kiểm tra giảng viên đang hoạt động, lịch đầy đủ và chưa kết thúc, thời lượng/số lượt dương, có lớp hợp lệ, có ít nhất một câu hỏi hoạt động cùng môn với điểm dương. Câu trắc nghiệm cần 2–10 lựa chọn không trống, không trùng nội dung và đúng một đáp án đúng; tự luận không có lựa chọn. Chưa đủ điều kiện trả HTTP 400, trạng thái vẫn DRAFT.

Nút **Công bố** tải lại cấu hình hiện tại trước khi xác nhận. Nếu thiếu lịch, lịch đã hết hạn hoặc chưa giao lớp, giao diện mở biểu mẫu sửa và chỉ rõ điều kiện còn thiếu. Lưu cấu hình rồi nhấn Công bố lại. Nếu chưa có câu hỏi, thông báo hướng dẫn vào **Câu hỏi**. Khi đủ các điều kiện này, hộp xác nhận trong giao diện hiển thị lịch, lớp, số câu và thời lượng; **Hủy** giữ nháp, **Xác nhận công bố** gửi yêu cầu lên backend. Backend vẫn kiểm tra toàn bộ tính hợp lệ của câu hỏi và quyền công bố; lỗi được hiển thị ngay trong hộp xác nhận.

Nếu đề đã được công bố hoặc đóng từ tab khác, thao tác Công bố cập nhật danh sách và thông báo trạng thái hiện tại. Trường hợp trạng thái đổi trong lúc hộp xác nhận đang mở, frontend đọc lại đề sau HTTP 409 và xử lý tương tự. Nút **Làm mới** cho phép cập nhật danh sách theo yêu cầu.

Task 8 đã bổ sung nút **Câu hỏi** để gán câu hỏi vào bài, đặt điểm, sắp xếp và xem thử đề theo cấu hình trộn. Bài nháp mới tạo cần gán câu hỏi trước khi công bố. Xem [hướng dẫn Task 8](exam-questions.md).

Xóa nháp chỉ xóa liên kết câu hỏi/lớp của bài, giữ câu hỏi trong ngân hàng. Nếu bài có lịch sử lượt thi, trả HTTP 409 để giữ lịch sử. Response không chứa nội dung câu hỏi, lựa chọn hay đáp án đúng.

## Chạy và kiểm thử

Giữ MySQL Docker đang chạy trên localhost:3306, database exam_guard; chạy backend và frontend theo README. Mở http://localhost:5173, đăng nhập `demo_teacher01` hoặc `demo_admin01`, mật khẩu `Demo@123`, chọn **Quản lý bài thi**.

1. Tạo bài nháp, chọn môn và lớp của giảng viên; đặt thời lượng, số lượt, chính sách kết quả, lịch mở/đóng.
2. Tìm theo tiêu đề, lọc môn/trạng thái; xem chi tiết và sửa cấu hình. Kiểm tra lịch khi mở lại biểu mẫu.
3. Thử công bố bài chưa có câu hỏi: nhận thông báo thiếu điều kiện và giữ nháp.
4. Với bài có câu hỏi hợp lệ, lịch tương lai và lớp được giao: công bố, xác nhận không còn sửa/xóa; đóng bài, trạng thái chuyển CLOSED.
5. Admin tạo bài cho giảng viên khác; danh sách lớp phải theo giảng viên đã chọn. Giảng viên khác không thấy bài trong danh sách của mình.
6. Xóa bài nháp thử nghiệm; câu hỏi ngân hàng và lịch sử bài khác được giữ.

Chạy toàn bộ kiểm thử backend (H2 và MySQL thật):

```powershell
cd ExamGuardBE
$env:EXAMGUARD_MYSQL_TEST = 'true'
.\mvnw.cmd test '-Dspring.jpa.show-sql=false'
```

Các ca Task 7 dùng dữ liệu có tiền tố riêng và transaction rollback: CRUD/UTC, phân trang/lọc/phạm vi, role/ownership, validation, lớp/giảng viên, điều kiện công bố, trạng thái và giữ lịch sử. Giao diện: `npm run lint`, `npm run build` trong ExamGuardFE.
