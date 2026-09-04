import { http } from '@/utils/request'
import type { ActiveBannerVO, SingerVO, SongVO } from './types'

/** 全站热门榜 */
export function getHotChart(limit = 10) {
  return http.get<SongVO[]>('/song/chart', { limit })
}

/** 新歌首发 */
export function getNewSongs(limit = 10) {
  return http.get<SongVO[]>('/song/new', { limit })
}

/** 猜你喜欢：登录用户按风格标签画像加权混采推荐（收听历史+收藏双信号），未登录走热门兜底 */
export function getGuessSongs(limit = 10) {
  return http.get<SongVO[]>('/song/recommend', { limit })
}

/** 推荐歌手 */
export function getHotSingers(limit = 10) {
  return http.get<SingerVO[]>('/singer/hot', { limit })
}

/** 当前上架的轮播图（匿名可访） */
export function getActiveBanners() {
  return http.get<ActiveBannerVO[]>('/admin/banner/active')
}
