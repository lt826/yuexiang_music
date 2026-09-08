import request, { UPLOAD_TIMEOUT } from './request'
import type { SongVO } from './home'

/** 歌手信息（含认证状态） */
export interface SingerVO {
  id: number
  userId: number | null
  name: string
  avatar: string | null
  introduction: string | null
  /** 0待审核 1正常 2驳回 3冻结 */
  status: number
  rejectReason: string | null
  createTime: string
}

/** 提交歌手认证申请 */
export function applySinger(data: { name: string; introduction?: string; avatar?: string }) {
  return request.post<unknown, void>('/singer/apply', data)
}

/** 我的申请状态（未申请返回 null） */
export function getMyApplication() {
  return request.get<unknown, SingerVO | null>('/singer/my-application')
}

/** 歌手更新自己的资料（艺名即歌曲展示的发布者名字） */
export function updateSingerProfile(data: { name?: string; introduction?: string; avatar?: string }) {
  return request.put<unknown, void>('/singer/my-profile', data)
}

/** 歌手主页 */
export function getSingerProfile(id: number) {
  return request.get<unknown, SingerVO>(`/singer/profile/${id}`)
}

/** 歌手主页聚合信息（资料 + 作品数/总播放/粉丝数 + 当前用户关注态） */
export interface SingerHomeVO {
  id: number
  userId: number | null
  name: string
  avatar: string | null
  introduction: string | null
  songCount: number
  totalPlayCount: number
  followerCount: number
  followed: boolean
}

/** 歌手主页聚合（匿名可看，登录后 followed 为真实关注态） */
export function getSingerHome(id: number) {
  return request.get<unknown, SingerHomeVO>(`/singer/${id}/home`)
}

/** 关注歌手 */
export function followSinger(id: number) {
  return request.post<unknown, void>(`/singer/${id}/follow`)
}

/** 取消关注歌手 */
export function unfollowSinger(id: number) {
  return request.delete<unknown, void>(`/singer/${id}/follow`)
}

/** 我的关注列表项 */
export interface SingerFollowItem {
  singerId: number
  name: string
  avatar: string | null
  introduction: string | null
  followTime: string
}

/** 我的关注列表 */
export function getMyFollows() {
  return request.get<unknown, SingerFollowItem[]>('/singer/my-follows')
}

/** 我关注的歌手ID集合（播放页关注态回显） */
export function getMyFollowIds() {
  return request.get<unknown, number[]>('/singer/my-follow-ids')
}

/** 搜歌手（搜索页"相关歌手"区） */
export function searchSingers(keyword: string, limit = 6) {
  return request.get<unknown, SingerVO[]>('/singer/search', { params: { keyword, limit } })
}

/** 歌手的已发布歌曲 */
export function getSongsBySinger(id: number) {
  return request.get<unknown, SongVO[]>(`/song/by-singer/${id}`)
}

// ===== 歌手作品管理 =====

export interface SongPublishDTO {
  name: string
  url: string
  cover?: string | null
  lyric?: string | null
  style?: string | null
}

/** 发布歌曲（音频先传 file 服务） */
export function publishSong(data: SongPublishDTO) {
  return request.post<unknown, void>('/song/publish', data)
}

/** 我的歌曲列表（各状态） */
export function getMySongs() {
  return request.get<unknown, SongVO[]>('/song/my')
}

/** 修改歌曲信息 */
export function updateSong(id: number, data: Partial<SongPublishDTO>) {
  return request.put<unknown, void>(`/song/${id}`, data)
}

/** 下架歌曲 */
export function offlineSong(id: number) {
  return request.post<unknown, void>(`/song/${id}/offline`)
}

/** 已下架歌曲重新提交上架审核 */
export function resubmitSong(id: number) {
  return request.post<unknown, void>(`/song/${id}/resubmit`)
}

// ===== 文件上传 =====

export interface FileVO {
  id: number
  originalName: string
  url: string
  type: string
  size: number
}

/** 上传文件（type: SONG/COVER/AVATAR） */
export function uploadFile(file: File, type: string) {
  const form = new FormData()
  form.append('file', file)
  form.append('type', type)
  return request.post<unknown, FileVO>('/file/upload', form, { timeout: UPLOAD_TIMEOUT })
}
