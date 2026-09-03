import request from './request'
import type { SongVO } from './home'
import type { SingerVO } from './singer'

/** 管理员登录返回（独立于普通用户登录） */
export interface AdminLoginVO {
  token: string
  adminId: number
  username: string
  nickname: string | null
  role: string
}

export function adminLogin(data: { username: string; password: string }) {
  return request.post<unknown, AdminLoginVO>('/admin/login', data)
}

/** 管理端用户项（user 服务 UserVO） */
export interface AdminUserVO {
  id: number
  username: string
  nickname: string | null
  email: string | null
  avatar: string | null
  phone: string | null
  role: string
  status: number
  createTime: string
}

/** 分页用户列表 */
export function adminUserList(keyword?: string, page = 1, size = 10) {
  return request.get<unknown, { items: AdminUserVO[]; total: number }>('/admin/user/list', {
    params: { keyword, page, size },
  })
}

/** 新增用户 */
export function adminUserCreate(data: {
  username: string
  password: string
  email: string
  nickname?: string
  phone?: string
  role?: string
}) {
  return request.post<unknown, void>('/admin/user/create', data)
}

/** 编辑用户（不传的字段不动；password 留空不重置） */
export function adminUserUpdate(
  userId: number,
  data: { nickname?: string; email?: string; phone?: string; role?: string; status?: number; password?: string },
) {
  return request.put<unknown, void>('/admin/user/update', data, { params: { userId } })
}

/** 删除用户（逻辑删除） */
export function adminUserDelete(userId: number) {
  return request.delete<unknown, void>('/admin/user/delete', { params: { userId } })
}

/** 禁用/启用用户 */
export function adminUserStatus(userId: number, enable: boolean) {
  return request.post<unknown, void>('/admin/user/status', null, { params: { userId, enable } })
}

/** 待审核歌手 */
export function adminPendingSingers() {
  return request.get<unknown, SingerVO[]>('/admin/singer/pending')
}

/** 审核歌手 */
export function adminAuditSinger(id: number, approve: boolean, reason?: string) {
  return request.post<unknown, void>(`/admin/singer/audit/${id}`, null, { params: { approve, reason } })
}

/** 待审核歌曲 */
export function adminPendingSongs() {
  return request.get<unknown, SongVO[]>('/admin/song/pending')
}

/** 审核歌曲 */
export function adminAuditSong(id: number, approve: boolean, reason?: string) {
  return request.post<unknown, void>(`/admin/song/audit/${id}`, null, { params: { approve, reason } })
}

/** 管理端全量歌曲列表（含待审/下架，分页+关键字） */
export function adminSongList(keyword?: string, page = 1, size = 10) {
  return request.get<unknown, { items: SongVO[]; total: number }>('/admin/song/list', {
    params: { keyword, page, size },
  })
}

/** 管理员强制下架歌曲（通知歌手） */
export function adminSongOffline(songId: number, reason?: string) {
  return request.post<unknown, void>('/admin/song/offline', null, { params: { songId, reason } })
}

/** 群发公告 */
export function adminBroadcast(title: string, content: string) {
  return request.post<unknown, void>('/admin/broadcast', null, { params: { title, content } })
}

// ===== 轮播图管理 =====

/** 轮播图项 */
export interface BannerVO {
  id: number
  title: string
  subtitle: string | null
  imageUrl: string
  songId: number | null
  sortOrder: number
  status: number
  createTime: string
}

/** 全部轮播图（管理端，含下架项） */
export function adminBannerList() {
  return request.get<unknown, BannerVO[]>('/admin/banner/list')
}

/** 新增/编辑轮播图（有 id 为编辑） */
export function adminBannerSave(data: {
  id?: number
  title: string
  subtitle?: string | null
  imageUrl: string
  songId?: number | null
  sortOrder?: number
  status?: number
}) {
  return request.post<unknown, void>('/admin/banner/save', data)
}

/** 删除轮播图 */
export function adminBannerDelete(id: number) {
  return request.delete<unknown, void>('/admin/banner/delete', { params: { id } })
}
