import { http } from '@/utils/request'
import type { LoginVO, UserInfo } from './types'

export function login(data: { username: string; password: string }) {
  return http.post<LoginVO>('/user/login', data)
}

export function register(data: { username: string; email: string; password: string }) {
  return http.post<void>('/user/register', data)
}

export function getUserInfo() {
  return http.get<UserInfo>('/user/info')
}

export function updateProfile(data: { nickname?: string; signature?: string }) {
  return http.put<UserInfo>('/user/profile', data)
}

export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return http.post<void>('/user/password', data)
}
