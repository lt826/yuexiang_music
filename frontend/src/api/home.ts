import request from './request'

export interface SongVO {
  id: number
  name: string
  cover: string | null
  url: string | null
  /** LRC 歌词（播放接口下发，列表接口为空） */
  lyric?: string | null
  style: string | null
  playCount: number
  singerId: number
  singerName: string | null
  /** 审核状态 0待审核 1已发布 2已下架（我的歌曲列表下发） */
  status?: number
  /** 审核驳回原因（status 相关场景下发） */
  rejectReason?: string | null
}

export interface SingerVO {
  id: number
  name: string
  avatar: string | null
  introduction: string | null
}

/** 全站热门榜 */
export function getHotChart(limit = 10) {
  return request.get<unknown, SongVO[]>('/song/chart', { params: { limit } })
}

/** 新歌首发（按上架时间倒序） */
export function getNewSongs(limit = 10) {
  return request.get<unknown, SongVO[]>('/song/new', { params: { limit } })
}

/** 推荐歌曲（未登录走热门兜底） */
export function getRecommendSongs(limit = 10) {
  return request.get<unknown, SongVO[]>('/song/recommend', { params: { limit } })
}

/** 猜你喜欢：登录用户按收听历史歌手画像推荐，未登录/无历史随机兜底 */
export function getGuessSongs(limit = 10) {
  return request.get<unknown, SongVO[]>('/song/guess', { params: { limit } })
}

/** 推荐歌手 */
export function getHotSingers(limit = 10) {
  return request.get<unknown, SingerVO[]>('/singer/hot', { params: { limit } })
}
