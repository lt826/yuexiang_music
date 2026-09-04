/** 与后端对齐的 VO 类型（对齐 Web 端 src/api/*.ts） */

export interface SongVO {
  id: number
  name: string
  cover: string | null
  url: string | null
  lyric?: string | null
  style: string | null
  playCount: number
  singerId: number
  singerName: string | null
  status?: number
  rejectReason?: string | null
}

export interface SingerVO {
  id: number
  userId: number | null
  name: string
  avatar: string | null
  introduction: string | null
  status: number
  rejectReason: string | null
  createTime: string
}

export interface LoginVO {
  token: string
  userId: number
  username: string
  nickname: string
  avatar: string | null
  role: string
}

export interface UserInfo {
  id: number
  username: string
  nickname: string | null
  avatar: string | null
  phone: string | null
  email: string | null
  gender: number
  signature: string | null
  role: string
}

export interface PlaylistVO {
  id: number
  userId: number
  title: string
  cover: string | null
  description: string | null
  tag: string | null
  isOfficial: number
  playCount: number
  songCount: number
  favored: boolean | null
  createTime: string
}

export interface PlaylistSongItem {
  id: number
  name: string
  cover: string | null
  url: string | null
  style: string | null
  playCount: number
  singerId: number
  singerName: string | null
}

export interface PlaylistDetailVO {
  playlist: PlaylistVO
  songs: PlaylistSongItem[]
}

export interface FavoriteItemVO {
  songId: number
  name: string | null
  cover: string | null
  url: string | null
  style: string | null
  playCount: number | null
  singerName: string | null
  time: string
}

export interface HistoryPageVO {
  items: FavoriteItemVO[]
  page: number
  size: number
}

/** 听歌周报（最近 7 天播放统计） */
export interface WeeklyReportVO {
  playCount: number
  songCount: number
  activeDays: number
  topSongs: WeeklyTopSong[]
  topSingers: WeeklyTopSinger[]
}

export interface WeeklyTopSong {
  songId: number
  name: string | null
  singerName: string | null
  cover: string | null
  count: number
}

export interface WeeklyTopSinger {
  name: string
  count: number
}

export interface ActiveBannerVO {
  id: number
  title: string
  subtitle: string | null
  imageUrl: string
  songId: number | null
}
