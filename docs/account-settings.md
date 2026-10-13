# Cài đặt tài khoản cá nhân

Nút **Cài đặt tài khoản** nằm bên cạnh Đăng xuất, dùng cho ADMIN/TEACHER/STUDENT. Biểu mẫu đọc lại hồ sơ mới nhất khi mở. Thông tin cá nhân gồm họ tên, tên đăng nhập, email. Server lấy ID từ tài khoản xác thực; không nhận ID, role, enabled hay password trong request hồ sơ để đổi người dùng/quyền/mật khẩu.

Tên đăng nhập được chuẩn hóa chữ thường, 3–50 ký tự chữ/số/gạch dưới. Họ tên không trống, tối đa 150 ký tự; email hợp lệ, tối đa 254 ký tự, chuẩn hóa chữ thường. Email/tên đăng nhập trùng tài khoản khác trả 409; giữ nguyên thông tin cũ. Lưu thành công cập nhật thanh tài khoản ngay; token hiện tại tiếp tục dùng được vì chủ thể xác thực là ID. Lần đăng nhập tiếp theo dùng tên mới.

Đổi mật khẩu yêu cầu mật khẩu hiện tại đúng, mật khẩu mới ít nhất 8 ký tự, tối đa 72 byte UTF-8 và xác nhận khớp. Mật khẩu không được trim, không lưu trên trình duyệt hay ghi vào log. Server mã hóa BCrypt, không trả passwordHash. Sai mật khẩu hiện tại/xác nhận/giới hạn trả 400, không thay hash.

Biểu mẫu hiển thị lỗi ngay trong cửa sổ, khóa các nút/field trong khi đang lưu. Cửa sổ cài đặt giữ dashboard/phiên thi đang mở phía dưới; mở cài đặt không dừng bộ đếm hay autosave. Đổi mật khẩu thành công đăng xuất và yêu cầu đăng nhập lại; các đáp án đã lưu và hạn thi vẫn giữ nguyên.

## API

Bearer token của người đang đăng nhập, không hạn chế theo role.

| Method | Endpoint | Chức năng |
| --- | --- | --- |
| GET | `/api/auth/me` | Đọc hồ sơ hiện tại (đã có) |
| PUT | `/api/auth/me` | Sửa họ tên, tên đăng nhập và email của chính mình |
| PUT | `/api/auth/password` | Đổi mật khẩu bằng mật khẩu hiện tại, vô hiệu hóa token cũ |

PUT me:

```json
{
  "fullName": "Nguyễn Văn An",
  "username": "nguyenvanan",
  "email": "nguyenvanan@example.test"
}
```

PUT password:

```json
{
  "currentPassword": "mat-khau-hien-tai",
  "newPassword": "mat-khau-moi",
  "confirmPassword": "mat-khau-moi"
}
```

Các chuỗi mật khẩu trong ví dụ chỉ là placeholder. Response hồ sơ dùng UserResponse trong ApiResponse; response đổi mật khẩu có status/message và không có dữ liệu nhạy cảm. Thiếu/sai token hoặc tài khoản bị khóa trả 401.

## Vô hiệu hóa phiên cũ

JWT mới có credentialStamp là HMAC-SHA256 của ngữ cảnh, userId và passwordHash bằng signing key hiện có. JWT không chứa hash BCrypt hoặc mật khẩu. Khi nhận request hoặc introspect, server so sánh stamp với tài khoản hiện tại; đổi/đặt lại mật khẩu làm stamp khác, token cũ bị từ chối ở những request tiếp theo. Hồ sơ đổi tên/email không làm mất hiệu lực token. Không thêm bảng hoặc cột database.

Token cũ thiếu stamp trước phiên bản này cũng bị từ chối; người dùng cần đăng nhập lại một lần khi cập nhật backend. Đây không phải việc đặt lại mật khẩu tài khoản. Các lần sửa profile/đổi mật khẩu/admin sửa người dùng dùng khóa cùng bản ghi User để tránh ghi đè hash/quyền do cập nhật đồng thời.

## Kiểm chứng

Toàn bộ kiểm thử backend đạt **137/137**, chạy H2 và MySQL thật, không bỏ qua. Các ca mới kiểm tra ba role, ID/role/enabled/password giả từ client, chuẩn hóa, trùng username/email, validation, thay tên không mất token, mật khẩu hiện tại/xác nhận/giới hạn byte, token từ nhiều phiên bị vô hiệu hóa, introspect token cũ, đăng nhập bằng mật khẩu mới, admin reset và tài khoản bị khóa. Fixture có transaction rollback.

Frontend đạt **12/12** kiểm thử (gồm xác nhận/độ dài/UTF-8 và các ca autosave), lint/build đạt. Kiểm tra trình duyệt bằng tài khoản fixture riêng: mở cài đặt, sửa hồ sơ, tên hiển thị đổi ngay, email trùng hiển thị lỗi và sửa lại được, reload giữ hồ sơ. Mật khẩu tài khoản demo/tài khoản thật không được đổi trong kiểm thử giao diện; luồng thay mật khẩu được kiểm tra bằng API trong các test rollback. Tài khoản fixture giao diện được dọn sau kiểm tra.
