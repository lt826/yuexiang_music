import { http } from '@/utils/request'
import type { FavoriteItemVO, HistoryPageVO, WeeklyReportVO } from './types'

/** 收藏歌曲 */
export function addFavorite(songId: number) {
  return http.post<void>(`/favorite/song/${songId}`)
}

/** 取消收藏 */
export function removeFavorite(songId: number) {
  return http.delete<void>(`/favorite/song/${songId}`)
}

/** 我的收藏列表 */
export function getFavoriteList() {
  return http.get<FavoriteItemVO[]>('/favorite/song/list')
}

/** 我收藏的歌曲ID集合 */
export function getFavoriteSongIds() {
  return http.get<number[]>('/favorite/song/ids')
}

/** 记录收听历史 */
export function recordHistory(songId: number) {
  return http.post<void>(`/favorite/history/${songId}`)
}

/** 收听历史分页 */
export function getHistoryList(page = 1, size = 20) {
  return http.get<HistoryPageVO>('/favorite/history/list', { page, size })
}

/** 听歌周报：最近 7 天播放统计 */
export function getWeeklyReport() {
  return http.get<WeeklyReportVO>('/favorite/report/weekly')
}
