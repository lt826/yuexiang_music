import request from './request'

/** 登录返回（对齐详细设计 12.2：token、userId、nickname、avatar、role） */
export interface LoginVO {
  token: string
  userId: number
  username: string
  nickname: string
  avatar: string | null
  role: string
}

export interface UserInfo {
  id: number
  username: string
  nickname: string | null
  avatar: string | null
  phone: string | null
  email: string | null
  gender: number
  signature: string | null
  role: string
}

export function login(data: { username: string; password: string }) {
  return request.post<unknown, LoginVO>('/user/login', data)
}

export function register(data: { username: string; email: string; password: string }) {
  return request.post<unknown, void>('/user/register', data)
}

export function getUserInfo() {
  return request.get<unknown, UserInfo>('/user/info')
}

/** 修改昵称与签名（个人中心），返回更新后的用户信息 */
export function updateProfile(data: { nickname?: string; signature?: string }) {
  return request.put<unknown, UserInfo>('/user/profile', data)
}

/** 修改头像（URL 由 file 服务上传返回），返回更新后的用户信息 */
export function updateAvatar(avatar: string) {
  return request.put<unknown, UserInfo>('/user/avatar', { avatar })
}

/** 修改密码（校验旧密码，成功后需重新登录） */
export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request.post<unknown, void>('/user/password', data)
}
