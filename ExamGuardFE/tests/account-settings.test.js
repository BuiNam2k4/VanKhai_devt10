import test from 'node:test'
import assert from 'node:assert/strict'
import { passwordChangeError } from '../src/account-settings.js'

test('changing a password requires all fields and matching confirmation', () => {
  assert.notEqual(passwordChangeError({ currentPassword: '', newPassword: 'New@12345', confirmPassword: 'New@12345' }), '')
  assert.match(passwordChangeError({ currentPassword: 'Old@12345', newPassword: 'New@12345', confirmPassword: 'Different@123' }), /không khớp/)
})

test('the BCrypt limit counts UTF-8 bytes rather than visible characters', () => {
  const password = 'á'.repeat(37)
  assert.match(passwordChangeError({ currentPassword: 'Old@12345', newPassword: password, confirmPassword: password }), /72 byte/)
  const valid = 'á'.repeat(36)
  assert.equal(passwordChangeError({ currentPassword: 'Old@12345', newPassword: valid, confirmPassword: valid }), '')
})

test('a short new password is rejected while a valid password is accepted', () => {
  assert.notEqual(passwordChangeError({ currentPassword: 'Old@12345', newPassword: 'short', confirmPassword: 'short' }), '')
  assert.equal(passwordChangeError({ currentPassword: 'Old@12345', newPassword: 'New@12345', confirmPassword: 'New@12345' }), '')
})
