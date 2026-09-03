import { BASE_URL, API_PREFIX } from '@/config'

/** 后端统一响应结构（对齐 Web 端 request.ts） */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

interface Options {
  url: string
  method?: Method
  /** 请求体（POST/PUT 的 JSON） */
  data?: Record<string, unknown>
  /** URL 查询参数（GET） */
  params?: Record<string, unknown>
  timeout?: number
}

function buildUrl(url: string, params?: Record<string, unknown>): string {
  // 业务路径统一补网关前缀（/song/chart → /api/song/chart）
  let full = BASE_URL + (url.startsWith(API_PREFIX) ? '' : API_PREFIX) + url
  if (params) {
    const qs = Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
      .join('&')
    if (qs) full += (full.includes('?') ? '&' : '?') + qs
  }
  return full
}

/** 与 stores/user.ts 的 TOKEN_KEY 保持一致 */
const TOKEN_KEY = 'yx_token'

/** 清登录态并回登录页（401 统一处理） */
function forceLogout() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.showToast({ title: '登录已失效，请重新登录', icon: 'none' })
  setTimeout(() => {
    uni.reLaunch({ url: '/pages/login/login' })
  }, 600)
}

export function request<T>(options: Options): Promise<T> {
  const { url, method = 'GET', data, params, timeout } = options
  const full = buildUrl(url, params)
  const token = uni.getStorageSync(TOKEN_KEY) as string

  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: full,
      method,
      data: data as string | Record<string, unknown> | undefined,
      timeout: timeout ?? 10000,
      header: token ? { Authorization: token } : {},
      success: (res) => {
        if (res.statusCode === 200) {
          const body = res.data as Result<T>
          if (body.code === 200) {
            resolve(body.data)
            return
          }
          // 业务错误码：toast 提示后 reject（对齐 Web 端拦截器行为）
          uni.showToast({ title: body.message || '操作失败', icon: 'none' })
          reject(new Error(body.message))
          return
        }
        if (res.statusCode === 401) {
          forceLogout()
        } else {
          const msg = (res.data as Result | undefined)?.message || '网络异常，请稍后重试'
          uni.showToast({ title: msg, icon: 'none' })
        }
        reject(new Error(`HTTP ${res.statusCode}`))
      },
      fail: () => {
        uni.showToast({ title: '网络异常，请检查后端服务', icon: 'none' })
        reject(new Error('network error'))
      },
    })
  })
}

export const http = {
  get: <T>(url: string, params?: Record<string, unknown>) => request<T>({ url, params }),
  post: <T>(url: string, data?: Record<string, unknown>, timeout?: number) =>
    request<T>({ url, method: 'POST', data, timeout }),
  put: <T>(url: string, data?: Record<string, unknown>) => request<T>({ url, method: 'PUT', data }),
  delete: <T>(url: string, params?: Record<string, unknown>) =>
    request<T>({ url, method: 'DELETE', params }),
}
