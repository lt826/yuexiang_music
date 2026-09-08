import request from './request'

/** 平台实时统计（/admin/stats，网关白名单匿名可访） */
export interface PortalStatsVO {
  /** 注册用户总数 */
  userCount: number
  /** 驻站歌手数 */
  singerCount: number
  /** 正常状态账号数 */
  activeCount: number
  /** 歌曲总数（含待审/下架） */
  songCount: number
  /** 已发布歌曲数 */
  publishedCount: number
  /** 已发布歌曲总播放量 */
  playCount: number
}

/** 门户首页实时统计（30s 轮询刷新） */
export function getPortalStats() {
  return request.get<unknown, PortalStatsVO>('/admin/stats')
}

/** 当前上架的轮播图项（/admin/banner/active，匿名可访） */
export interface ActiveBannerVO {
  id: number
  title: string
  subtitle: string | null
  imageUrl: string
  songId: number | null
}

export function getActiveBanners() {
  return request.get<unknown, ActiveBannerVO[]>('/admin/banner/active')
}
