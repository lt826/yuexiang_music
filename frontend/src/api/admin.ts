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

// ===== 歌单管理（官方歌单含 8 个电台节目单）=====

/** 管理端歌单项 */
export interface AdminPlaylistVO {
  id: number
  userId: number
  title: string
  cover: string | null
  description: string | null
  tag: string | null
  isOfficial: number
  published: number
  playCount: number
  songCount: number
}

/** 全部歌单（官方 + 用户公开，管理端视角） */
export function adminPlaylistList() {
  return request.get<unknown, AdminPlaylistVO[]>('/admin/playlist/list')
}

/** 歌单内歌曲（按节目顺序） */
export function adminPlaylistDetail(playlistId: number) {
  return request.get<unknown, SongVO[]>(`/admin/playlist/${playlistId}/songs`)
}

/** 管理员往歌单加歌（官方歌单维护电台节目单等） */
export function adminPlaylistAddSong(playlistId: number, songId: number) {
  return request.post<unknown, void>(`/admin/playlist/${playlistId}/songs/${songId}`)
}

/** 管理员从歌单移除歌曲 */
export function adminPlaylistRemoveSong(playlistId: number, songId: number) {
  return request.delete<unknown, void>(`/admin/playlist/${playlistId}/songs/${songId}`)
}
