import request from './request'

/** 收藏/历史列表项（favorite 服务聚合 song 信息后下发） */
export interface FavoriteItemVO {
  songId: number
  name: string | null
  cover: string | null
  url: string | null
  style: string | null
  playCount: number | null
  singerName: string | null
  time: string
}

export interface HistoryPageVO {
  items: FavoriteItemVO[]
  page: number
  size: number
}

/** 听歌周报（最近 7 天播放统计） */
export interface WeeklyReportVO {
  playCount: number
  songCount: number
  activeDays: number
  topSongs: WeeklyTopSong[]
  topSingers: WeeklyTopSinger[]
}

export interface WeeklyTopSong {
  songId: number
  name: string | null
  singerName: string | null
  cover: string | null
  count: number
}

export interface WeeklyTopSinger {
  name: string
  count: number
}

/** 听歌周报：最近 7 天播放统计 */
export function getWeeklyReport() {
  return request.get<unknown, WeeklyReportVO>('/favorite/report/weekly')
}

/** 收藏歌曲 */
export function addFavorite(songId: number) {
  return request.post<unknown, void>(`/favorite/song/${songId}`)
}

/** 取消收藏 */
export function removeFavorite(songId: number) {
  return request.delete<unknown, void>(`/favorite/song/${songId}`)
}

/** 我的收藏列表 */
export function getFavoriteList() {
  return request.get<unknown, FavoriteItemVO[]>('/favorite/song/list')
}

/** 我收藏的歌曲ID集合（标注收藏态） */
export function getFavoriteSongIds() {
  return request.get<unknown, number[]>('/favorite/song/ids')
}

/** 记录收听历史（播放时触发） */
export function recordHistory(songId: number) {
  return request.post<unknown, void>(`/favorite/history/${songId}`)
}

/** 收听历史分页 */
export function getHistoryList(page = 1, size = 20) {
  return request.get<unknown, HistoryPageVO>('/favorite/history/list', { params: { page, size } })
}
