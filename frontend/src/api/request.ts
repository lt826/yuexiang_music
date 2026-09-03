import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { usePlayerStore } from '@/stores/player'

/** 后端统一响应结构（对齐说明书：code/message/data） */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 上传文件（音频最大 50MB）不适合默认 10s 超时，单独放宽
export const UPLOAD_TIMEOUT = 120000

// 响应拦截直接返回 res.data（解包），故实例类型视为只暴露 data
type UnwrappedAxios = ReturnType<typeof axios.create> & {
  get: <T = unknown, R = T>(url: string, config?: object) => Promise<R>
  post: <T = unknown, R = T>(url: string, data?: object, config?: object) => Promise<R>
  delete: <T = unknown, R = T>(url: string, config?: object) => Promise<R>
  put: <T = unknown, R = T>(url: string, data?: object, config?: object) => Promise<R>
}

// 请求拦截：注入 token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

// 响应拦截：统一解包与错误码处理
request.interceptors.response.use(
  (response) => {
    const res = response.data as Result
    if (res.code === 200) {
      return res.data as never
    }
    // 业务错误码（说明书 12.3 全局错误码表）
    ElMessage.error(res.message || '操作失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    if (error.response?.status === 401) {
      // 2006 Token 已过期：清登录态停歌回登录页
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      try {
        usePlayerStore().stop()
      } catch {
        // pinia 未就绪（极早期请求）时忽略
      }
      router.push('/login')
      ElMessage.error('登录已失效，请重新登录')
    } else {
      ElMessage.error(error.response?.data?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export default request as UnwrappedAxios
