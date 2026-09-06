import { http } from '@/utils/request'
import type { SongVO } from './types'

/** AI 返回的歌曲项（SongVO 字段子集，可直接交给播放器） */
export interface AiSongVO {
  id: number
  name: string
  singerName: string | null
  singerId: number | null
  style: string | null
  playCount: number
}

/** 单条对话消息 */
export interface AiChatMessage {
  role: 'user' | 'assistant'
  content: string
  /** assistant 消息附带的歌曲列表 */
  songs?: AiSongVO[]
  /** 本轮 AI 新建的歌单（生成歌单成功时后端回填） */
  createdPlaylist?: { id: number; title: string }
}

/** POST /ai/chat 响应 */
export interface AiChatVO {
  reply: string
  songs: AiSongVO[]
  createdPlaylistId: number | null
  createdPlaylistTitle: string | null
}

/** AI 聊天响应耗时较长（Function Calling 多轮），单独放宽超时（对齐 Web 端 120s） */
export function aiChat(message: string, history: AiChatMessage[]) {
  return http.post<AiChatVO>(
    '/ai/chat',
    {
      message,
      // 只回传文本角色与内容，歌曲列表由后端每轮重新检索
      history: history.map((m) => ({ role: m.role, content: m.content })),
    },
    120000
  )
}

/** GET /ai/radio/tune 响应：心情电台调频结果 */
export interface RadioTuneVO {
  channel: string
  /** 虚拟调频频率（88.4 等，调频盘展示） */
  freq: string
  playlistId: number | null
  playlistTitle: string | null
  songs: AiSongVO[]
  /** 当前正在播第几首（0 起） */
  currentIndex: number
  /** 当前这首已播到第几秒 */
  offsetSeconds: number
  /** 节目单时间轴的模拟单曲时长（秒） */
  segmentSeconds: number
}

/** 心情电台调频：拿频道节目单与当前应播位置（所有用户同一时刻结果一致） */
export function radioTune(channel: string) {
  return http.get<RadioTuneVO>('/ai/radio/tune', { channel })
}

/** AiSongVO → SongVO：url/lyric 等缺省字段由播放器 play/{id} 详情接口兜底 */
export function toSongVO(s: AiSongVO): SongVO {
  return {
    id: s.id,
    name: s.name,
    cover: null,
    url: null,
    lyric: null,
    style: s.style,
    playCount: s.playCount,
    singerId: s.singerId ?? 0,
    singerName: s.singerName,
  }
}
