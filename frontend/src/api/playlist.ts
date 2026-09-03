import request from './request'

/** 歌单信息 */
export interface PlaylistVO {
  id: number
  userId: number
  title: string
  cover: string | null
  description: string | null
  tag: string | null
  isOfficial: number
  /** 是否投稿到歌单广场 0私藏 1公开 */
  published: number
  playCount: number
  songCount: number
  favored: boolean | null
  createTime: string
}

/** 歌单详情（playlist 服务聚合 song 信息后的结构） */
export interface PlaylistDetailVO {
  playlist: PlaylistVO
  songs: PlaylistSongItem[]
}

/** 详情内歌曲项（song 服务 SongVO 的裸 Map 形态） */
export interface PlaylistSongItem {
  id: number
  name: string
  cover: string | null
  url: string | null
  style: string | null
  playCount: number
  singerId: number
  singerName: string | null
}

export interface PlaylistSaveDTO {
  title: string
  cover?: string | null
  description?: string | null
  tag?: string | null
  /** 是否投稿到歌单广场（编辑时切换） */
  published?: number
}

/** 官方推荐歌单（首页） */
export function getRecommendPlaylists(limit = 10) {
  return request.get<unknown, PlaylistVO[]>('/playlist/recommend', { params: { limit } })
}

/** 热门歌单（歌单广场） */
export function getHotPlaylists(limit = 20) {
  return request.get<unknown, PlaylistVO[]>('/playlist/hot', { params: { limit } })
}

/** 最新用户创建的歌单（歌单广场「乐友歌单」区） */
export function getUserCreatedPlaylists(limit = 20) {
  return request.get<unknown, PlaylistVO[]>('/playlist/user-created', { params: { limit } })
}

/** 可见歌单（官方+自己的） */
export function getAllPlaylists() {
  return request.get<unknown, PlaylistVO[]>('/playlist/all')
}

/** 我的歌单 */
export function getMyPlaylists() {
  return request.get<unknown, PlaylistVO[]>('/playlist/my')
}

/** 歌单详情（匿名可看） */
export function getPlaylistDetail(id: number) {
  return request.get<unknown, PlaylistDetailVO>(`/playlist/${id}`)
}

/** 创建歌单 */
export function createPlaylist(data: PlaylistSaveDTO) {
  return request.post<unknown, void>('/playlist/create', data)
}

/** 编辑歌单 */
export function updatePlaylist(id: number, data: Partial<PlaylistSaveDTO>) {
  return request.put<unknown, void>(`/playlist/${id}`, data)
}

/** 删除歌单 */
export function deletePlaylist(id: number) {
  return request.delete<unknown, void>(`/playlist/${id}`)
}

/** 添加歌曲到歌单 */
export function addSongToPlaylist(playlistId: number, songId: number) {
  return request.post<unknown, void>(`/playlist/${playlistId}/songs/${songId}`)
}

/** 从歌单移除歌曲 */
export function removeSongFromPlaylist(playlistId: number, songId: number) {
  return request.delete<unknown, void>(`/playlist/${playlistId}/songs/${songId}`)
}

/** 调整歌单内歌曲顺序（传全量 songId 有序列表） */
export function reorderPlaylistSongs(playlistId: number, songIds: number[]) {
  return request.put<unknown, void>(`/playlist/${playlistId}/songs/order`, songIds)
}

// ===== 歌单收藏 =====

/** 收藏歌单 */
export function favoritePlaylist(playlistId: number) {
  return request.post<unknown, void>(`/favorite/playlist/${playlistId}`)
}

/** 取消收藏歌单 */
export function unfavoritePlaylist(playlistId: number) {
  return request.delete<unknown, void>(`/favorite/playlist/${playlistId}`)
}

/** 我收藏的歌单ID列表 */
export function getFavoredPlaylistIds() {
  return request.get<unknown, number[]>('/favorite/playlist/list')
}
