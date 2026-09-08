import { http } from '@/utils/request'
import type { SongVO } from './types'

export interface SearchResultVO {
  total: number
  page: number
  size: number
  songs: SongVO[]
}

/** 关键字搜索 */
export function searchSongs(keyword: string, page = 1, size = 20) {
  return http.get<SearchResultVO>('/song/search', { keyword, page, size })
}

/** 播放：获取详情与歌词 */
export function getSongPlay(id: number) {
  return http.get<SongVO>(`/song/play/${id}`)
}

/** 播放上报（播完才计） */
export function reportPlay(id: number) {
  return http.post<void>(`/song/play/${id}/report`)
}

/** 歌手的已发布歌曲 */
export function getSongsBySinger(id: number) {
  return http.get<SongVO[]>(`/song/by-singer/${id}`)
}

/** 歌手主页 */
export function getSingerProfile(id: number) {
  return http.get<import('./types').SingerVO>(`/singer/profile/${id}`)
}
