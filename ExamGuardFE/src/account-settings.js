export function passwordChangeError({ currentPassword, newPassword, confirmPassword }) {
  if (!currentPassword || !newPassword || !confirmPassword) return 'Nhập đủ mật khẩu hiện tại, mật khẩu mới và xác nhận.'
  if (newPassword.length < 8) return 'Mật khẩu mới cần ít nhất 8 ký tự.'
  if (newPassword !== confirmPassword) return 'Mật khẩu xác nhận không khớp.'
  if (new TextEncoder().encode(currentPassword).length > 72 || new TextEncoder().encode(newPassword).length > 72) {
    return 'Mật khẩu không được vượt quá 72 byte UTF-8; ký tự có dấu có thể chiếm nhiều byte.'
  }
  return ''
}
