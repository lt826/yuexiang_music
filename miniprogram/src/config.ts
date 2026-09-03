/**
 * 小程序环境配置。
 *
 * BASE_URL 不再写死：vite.config.ts 在编译时自动探测本机局域网 IP 注入
 * __MP_BASE_URL__，换网络/换路由后直接 npm run build:mp-weixin 即可。
 * - 多网卡探测不对时：MP_BASE_IP=192.168.x.x npm run build:mp-weixin 强制指定
 * - 真机/发给朋友试用：手机与电脑须连同一网络，且电脑 IP 可达后端 8080
 * - 开发者工具「详情→本地设置」需勾选「不校验合法域名」（http + IP 非法域名）
 */
export const BASE_URL: string = __MP_BASE_URL__

/** 网关统一路由前缀（对齐 Web 端 axios baseURL: '/api'） */
export const API_PREFIX = '/api'

/** 后端返回的相对资源路径（如 /api/file/cover/xx.jpg）转成完整 URL */
export function fullUrl(path: string | null | undefined): string {
  if (!path) return ''
  if (/^https?:\/\//.test(path)) return path
  return BASE_URL + path
}
