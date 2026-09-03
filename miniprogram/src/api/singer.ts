import { http } from '@/utils/request'
import { BASE_URL, API_PREFIX } from '@/config'
import type { SingerVO, SongVO } from './types'

/** 提交歌手认证申请 */
export function applySinger(data: { name: string; introduction?: string }) {
  return http.post<void>('/singer/apply', data)
}

/** 我的申请状态（未申请返回 null） */
export function getMyApplication() {
  return http.get<SingerVO | null>('/singer/my-application')
}

/** 歌手更新自己的资料 */
export function updateSingerProfile(data: { name?: string; introduction?: string }) {
  return http.put<void>('/singer/my-profile', data)
}

// ===== 歌手作品管理 =====

/** 上传文件（type: SONG/COVER/AVATAR），返回可用的相对 URL */
export function uploadFile(
  filePath: string,
  type: 'SONG' | 'COVER' | 'AVATAR'
): Promise<{ url: string; originalName: string }> {
  const token = uni.getStorageSync('yx_token') as string
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: BASE_URL + API_PREFIX + '/file/upload',
      filePath,
      name: 'file',
      formData: { type },
      header: token ? { Authorization: token } : {},
      success: (res) => {
        try {
          const body = JSON.parse(res.data) as {
            code: number
            message: string
            data: { url: string; originalName: string }
          }
          if (body.code === 200) {
            resolve(body.data)
          } else {
            uni.showToast({ title: body.message || '上传失败', icon: 'none' })
            reject(new Error(body.message))
          }
        } catch {
          uni.showToast({ title: '上传失败', icon: 'none' })
          reject(new Error('upload parse error'))
        }
      },
      fail: () => {
        uni.showToast({ title: '网络异常，上传失败', icon: 'none' })
        reject(new Error('upload network error'))
      },
    })
  })
}

/** 发布歌曲（音频先传 file 服务，此处收 URL） */
export function publishSong(data: {
  name: string
  url: string
  cover?: string | null
  lyric?: string | null
  style?: string | null
}) {
  return http.post<void>('/song/publish', data)
}

/** 我的歌曲列表（含待审核/已下架） */
export function getMySongs() {
  return http.get<SongVO[]>('/song/my')
}

/** 下架歌曲 */
export function offlineSong(id: number) {
  return http.post<void>(`/song/${id}/offline`)
}

/** 已下架歌曲重新提交上架审核 */
export function resubmitSong(id: number) {
  return http.post<void>(`/song/${id}/resubmit`)
}
