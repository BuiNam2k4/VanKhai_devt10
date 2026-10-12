# Task 8 — Câu hỏi trong đề và trộn đề

## Sử dụng

Đăng nhập `demo_teacher01` hoặc `demo_admin01` với mật khẩu `Demo@123`, mở **Quản lý bài thi → Câu hỏi** trên bài nháp.

1. Tìm câu hỏi theo nội dung, loại và độ khó trong ngân hàng. Danh sách tự giới hạn cùng môn học và câu hỏi hoạt động, có phân trang 8 câu mỗi trang.
2. Bấm **+ Thêm** để chọn. Một câu chỉ được gán một lần; danh sách đã chọn được giữ khi đổi trang/bộ lọc.
3. Đặt điểm từng câu, dùng ↑/↓ để sắp xếp, **Bỏ** để gỡ khỏi đề. Tổng điểm được cập nhật trên giao diện.
4. Bấm **Lưu câu hỏi trong đề**. Khi mở lại, điểm và thứ tự lấy từ database. Bỏ khỏi đề giữ câu hỏi trong ngân hàng.
5. Đặt cờ trộn câu hỏi/đáp án trong **Sửa** cấu hình bài nháp. Mở **Câu hỏi → Xem thử đề thi** để xem thứ tự theo các cờ đã lưu. Lưu thay đổi câu hỏi trước khi xem thử.
6. Giao lớp và đặt lịch hợp lệ trong cấu hình bài, sau đó **Công bố**. Bài PUBLISHED/CLOSED chỉ xem câu hỏi và bản xem thử; không sửa điểm, thêm/bỏ hoặc đổi thứ tự.

Giảng viên quản lý đề của mình, thêm câu hỏi do mình tạo. Admin quản lý toàn bộ đề và chọn câu hỏi của mọi người tạo cùng môn. Giảng viên có thể giữ, đổi điểm/sắp xếp hoặc gỡ câu hỏi đã được admin gán sẵn vào đề của mình. Backend kiểm tra quyền trên từng API. Sinh viên không truy cập API quản lý/preview.

## API

Namespace `/api/teacher/exams/{examId}/questions` hoặc `/api/admin/exams/{examId}/questions`; gửi token trong Authorization header.

| Method | Đường dẫn sau namespace | Nội dung |
| --- | --- | --- |
| GET | / | Câu hỏi đã gán theo displayOrder, đáp án nguồn cho người quản lý, tổng điểm |
| PUT | / | Thay toàn bộ danh sách câu hỏi, điểm và thứ tự của bài nháp |
| POST | /preview | Xem thử đề theo cờ trộn hiện tại; không lưu hay tạo phiên thi |

PUT nhận:

```json
{
  "questions": [
    { "questionId": 12, "points": 2.50 },
    { "questionId": 9, "points": 3.00 }
  ]
}
```

Thay ID bằng dữ liệu thật. Thứ tự mảng là thứ tự nguồn, displayOrder được gán 1…N. Gửi `{"questions":[]}` để bỏ toàn bộ câu; trường questions bắt buộc, tối đa 1.000 mục. Response data gồm examId, title, status, totalPoints, questions; mỗi mục gồm id của ExamQuestion, question (QuestionResponse), points, displayOrder.

Điểm tối thiểu 0.01, tối đa 999999.99, tối đa hai chữ số thập phân; tổng điểm tối đa 999999.99 để phù hợp DECIMAL(8,2) của max_score. Không nhận câu trùng, câu khác môn, không hoạt động hoặc lựa chọn không hợp lệ. Trắc nghiệm cần 2–10 lựa chọn khác nội dung và đúng một đáp án đúng; tự luận không có lựa chọn. ID không tồn tại trả 404, dữ liệu sai trả 400, sai quyền trả 403, đề đã khóa hoặc có lịch sử phiên trả 409.

Thay danh sách trong một transaction, khóa đề và khóa câu hỏi theo ID tăng dần. Mọi câu được kiểm tra trước khi sửa liên kết. Giữ ID liên kết của câu còn trong đề; dùng thứ tự tạm trước khi sắp xếp để tránh vi phạm unique (exam_id, display_order) trên MySQL. Các câu bị bỏ được gỡ liên kết, ngân hàng không bị xóa.

Phân trang ngân hàng dùng API Question của Task 6 và giữ hợp đồng page/size/sortBy hiện có. API danh sách câu đã gán trả đầy đủ tập câu theo thứ tự của đề để lưu sắp xếp toàn bộ một cách nhất quán.

## Trộn đề và ảnh chụp phiên

`ExamPaperFactory` dùng Fisher–Yates để trộn câu và lựa chọn độc lập. Khi tắt cờ, giữ displayOrder nguồn. Khi bật, trộn trên bản sao trong bộ nhớ; nguồn ExamQuestion/QuestionOption không thay đổi. Lựa chọn giữ đúng ID nguồn, nội dung và cờ correct dù vị trí hiển thị đổi. Trộn ngẫu nhiên có thể cho cùng thứ tự giữa hai lần, đặc biệt với ít câu hoặc ít lựa chọn.

Preview trả nội dung, loại, điểm, thứ tự và nội dung các lựa chọn; không có correct, sourceOptionId hay ký hiệu đáp án đúng. Preview dùng cùng factory với ảnh chụp phiên nhưng mỗi lần xem thử được tạo độc lập.

`ExamPaperSnapshotService.createOrGet(sessionId)` là dịch vụ nội bộ chuẩn bị cho Task 10:

- Phải gọi bên trong transaction tạo phiên đã xác thực (Propagation.MANDATORY). Task 10 sẽ kiểm tra sinh viên, lịch, lớp, lượt còn lại và thiết bị trước khi cấp phiên.
- Khóa ExamSession để tuần tự hóa thao tác cùng phiên. Với phiên IN_PROGRESS thuộc bài PUBLISHED chưa có ảnh chụp, factory tạo bộ câu; service ghi SessionQuestion, SessionQuestionOption, displayOrder liên tục và maxScore bằng tổng điểm.
- Nội dung, loại, điểm, đáp án đúng được sao chép vào database. Lựa chọn nguồn chỉ làm liên kết; đáp án sinh viên sau này phải tham chiếu ID SessionQuestionOption.
- Gọi lại trả các bản ghi đã lưu theo displayOrder, không đọc lại ngân hàng hoặc trộn lại. Việc sửa nội dung ngân hàng không làm đổi ảnh chụp đã tạo.
- Lỗi tạo ảnh chụp làm transaction ngoài rollback cùng phiên. Dịch vụ trả entity nội bộ; API sinh viên của Task 10 cần map sang DTO loại bỏ correct và các dữ liệu chấm điểm trước khi trả ra ngoài.

Task 8 chưa cung cấp endpoint sinh viên bắt đầu lượt thi; phần này thuộc Task 10. Hai bảng ảnh chụp đã có trong schema Task 4 nên không cần migration database.

## Kiểm chứng

Đã chạy 69 kiểm thử backend, không lỗi, không bỏ qua, gồm H2 và MySQL Docker 8.0.36. Các ca Task 8 kiểm tra đổi thứ tự giữ ID trên unique constraint thật, gỡ liên kết giữ ngân hàng, validation/giới hạn điểm, ownership/role, công bố khóa câu hỏi, preview không chứa đáp án đúng, ảnh chụp giữ nội dung/thứ tự sau sửa ngân hàng và tạo lại không nhân đôi dữ liệu. Kiểm thử factory dùng nguồn số ngẫu nhiên cố định cho cả bốn tổ hợp cờ trộn, tránh kiểm thử phụ thuộc may rủi.

```powershell
cd ExamGuardBE
$env:EXAMGUARD_MYSQL_TEST = 'true'
.\mvnw.cmd test '-Dspring.jpa.show-sql=false'
```

Frontend được kiểm tra bằng lint/build và Edge headless trên backend/MySQL thật: chọn qua phân trang, lọc loại, chặn câu trùng, validation điểm, lưu/sắp xếp/xem lại, preview, gỡ câu, giao diện di động và đề đã công bố chỉ đọc. Dữ liệu thử có tiền tố riêng và được dọn sau khi kiểm tra.
