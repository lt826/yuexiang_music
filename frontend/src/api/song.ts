import request from './request'
import type { SongVO } from './home'

/** 搜索分页结果 */
export interface SearchResultVO {
  total: number
  page: number
  size: number
  songs: SongVO[]
}

/** 关键字搜索（FUN-03） */
export function searchSongs(keyword: string, page = 1, size = 20) {
  return request.get<unknown, SearchResultVO>('/song/search', { params: { keyword, page, size } })
}

/** 播放：获取详情与歌词 */
export function getSongPlay(id: number) {
  return request.get<unknown, SongVO>(`/song/play/${id}`)
}

/** 播放上报（播完才计） */
export function reportPlay(id: number) {
  return request.post<unknown, void>(`/song/play/${id}/report`)
}

/** 热搜词条目 */
export interface HotKeyword {
  keyword: string
  count: number
}

/** 实时热歌榜（Redis ZSet 播放热度） */
export function getRealtimeChart(limit = 10) {
  return request.get<unknown, SongVO[]>('/song/chart/realtime', { params: { limit } })
}

/** 全站热门榜（DB 播放统计；选歌弹窗的默认候选列表） */
export function getHotChart(limit = 10) {
  return request.get<unknown, SongVO[]>('/song/chart', { params: { limit } })
}

/** 热搜词 TopN（大家都在搜） */
export function getHotKeywords(limit = 10) {
  return request.get<unknown, HotKeyword[]>('/song/search/hot', { params: { limit } })
}

/** 我的搜索历史（最近在前，最多 10 条） */
export function getSearchHistory() {
  return request.get<unknown, string[]>('/song/search/history')
}

/** 清空搜索历史 */
export function clearSearchHistory() {
  return request.delete<unknown, void>('/song/search/history')
}
