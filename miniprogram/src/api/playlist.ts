import { http } from '@/utils/request'
import type { PlaylistDetailVO, PlaylistVO } from './types'

/** 官方推荐歌单（首页） */
export function getRecommendPlaylists(limit = 10) {
  return http.get<PlaylistVO[]>('/playlist/recommend', { limit })
}

/** 热门歌单（歌单广场） */
export function getHotPlaylists(limit = 20) {
  return http.get<PlaylistVO[]>('/playlist/hot', { limit })
}

/** 最新用户创建的歌单 */
export function getUserCreatedPlaylists(limit = 20) {
  return http.get<PlaylistVO[]>('/playlist/user-created', { limit })
}

/** 我的歌单 */
export function getMyPlaylists() {
  return http.get<PlaylistVO[]>('/playlist/my')
}

/** 歌单详情（匿名可看） */
export function getPlaylistDetail(id: number) {
  return http.get<PlaylistDetailVO>(`/playlist/${id}`)
}

/** 创建歌单，返回新歌单 ID */
export function createPlaylist(data: { title: string; description?: string; tag?: string }) {
  return http.post<number>('/playlist/create', data)
}

/** 删除歌单 */
export function deletePlaylist(id: number) {
  return http.delete<void>(`/playlist/${id}`)
}

/** 从歌单移除歌曲 */
export function removeSongFromPlaylist(playlistId: number, songId: number) {
  return http.delete<void>(`/playlist/${playlistId}/songs/${songId}`)
}

/** 添加歌曲到歌单 */
export function addSongToPlaylist(playlistId: number, songId: number) {
  return http.post<void>(`/playlist/${playlistId}/songs/${songId}`)
}

// ===== 歌单收藏 =====

export function favoritePlaylist(playlistId: number) {
  return http.post<void>(`/favorite/playlist/${playlistId}`)
}

export function unfavoritePlaylist(playlistId: number) {
  return http.delete<void>(`/favorite/playlist/${playlistId}`)
}
