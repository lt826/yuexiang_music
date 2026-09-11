import request from './request'
import type { SongVO } from './home'

/** 圈子 VO */
export interface CircleVO {
  id: number
  creatorId: number
  creatorName: string | null
  name: string
  description: string | null
  cover: string | null
  /** 审核状态 0待审核 1正常 2驳回 */
  status: number
  rejectReason: string | null
  memberCount: number
  postCount: number
  joined: boolean
  /** OWNER圈主 ADMIN圈管理员 MEMBER成员；未加入为 null */
  myRole: string | null
  liveRoom?: boolean
  liveRoomId?: number
  createTime: string
}

/** 圈内成员 VO */
export interface MemberVO {
  userId: number
  nickname: string | null
  avatar: string | null
  role: string
  joinTime: string
}

/** 帖子 VO */
export interface PostVO {
  id: number
  circleId: number
  userId: number
  nickname: string | null
  avatar: string | null
  content: string
  songId: number | null
  /** 歌曲卡片（song 服务聚合） */
  song: Partial<SongVO> | null
  /** 帖子图片URL（可空，最多1张） */
  image: string | null
  pinned: number
  likeCount: number
  commentCount: number
  liked: boolean
  canDelete: boolean
  canPin: boolean
  createTime: string
}

export interface PostPageVO {
  items: PostVO[]
  page: number
  size: number
}

/** 评论 VO（楼中楼） */
export interface CommentVO {
  id: number
  postId: number
  userId: number
  nickname: string | null
  avatar: string | null
  content: string
  replies: CommentVO[] | null
  canDelete: boolean
  createTime: string
}

/** 一起听房间状态 */
export interface ListenRoomVO {
  roomId: number
  circleId: number
  theme: string | null
  creatorId: number
  status: 'playing' | 'paused' | 'idle'
  actionAt: number
  position: number
  songId?: number
  song?: Partial<SongVO> | null
  memberCount: number
  circleName?: string
}

/** WS 在线成员 */
export interface ListenMemberVO {
  userId: number
  nickname: string
  avatar: string
  muted: boolean
  isManager: boolean
}

// ===== 圈子 =====

/** 乐坊广场（匿名可看） */
export function getSquare(keyword?: string, limit = 50) {
  return request.get<unknown, CircleVO[]>('/circle/square', { params: { keyword, limit } })
}

/** 我的圈子（创建+加入，含待审核的自己的） */
export function getMyCircles() {
  return request.get<unknown, CircleVO[]>('/circle/my')
}

/** 圈子详情（匿名可看） */
export function getCircleDetail(id: number) {
  return request.get<unknown, CircleVO>(`/circle/${id}`)
}

/** 创建圈子（名称进审核） */
export function createCircle(data: { name: string; description?: string; cover?: string }) {
  return request.post<unknown, number>('/circle/create', data)
}

/** 编辑圈子（改名重新审核） */
export function updateCircle(id: number, data: { name?: string; description?: string; cover?: string }) {
  return request.put<unknown, void>(`/circle/${id}`, data)
}

/** 解散圈子（仅圈主） */
export function dissolveCircle(id: number) {
  return request.delete<unknown, void>(`/circle/${id}`)
}

/** 加入圈子 */
export function joinCircle(id: number) {
  return request.post<unknown, void>(`/circle/${id}/join`)
}

/** 退出圈子 */
export function quitCircle(id: number) {
  return request.post<unknown, void>(`/circle/${id}/quit`)
}

/** 成员列表（圈内成员） */
export function getMembers(id: number) {
  return request.get<unknown, MemberVO[]>(`/circle/${id}/members`)
}

/** 踢出成员（圈主/圈管理员；管理员仅可踢普通成员） */
export function kickMember(id: number, targetUserId: number) {
  return request.post<unknown, void>(`/circle/${id}/member/${targetUserId}/kick`)
}

/** 设置/取消圈管理员（仅圈主；role: ADMIN | MEMBER） */
export function setMemberRole(id: number, targetUserId: number, role: 'ADMIN' | 'MEMBER') {
  return request.post<unknown, void>(`/circle/${id}/member/${targetUserId}/role`, null, {
    params: { role },
  })
}

// ===== 帖子 =====

/** 帖子流（置顶在前+时间倒序，匿名可看） */
export function getPosts(circleId: number, page = 1, size = 10) {
  return request.get<unknown, PostPageVO>(`/circle/${circleId}/posts`, { params: { page, size } })
}

/** 发帖（可插歌曲卡片/一张图片） */
export function createPost(
  circleId: number,
  data: { content: string; songId?: number; image?: string },
) {
  return request.post<unknown, number>(`/circle/${circleId}/posts`, data)
}

/** 删帖 */
export function deletePost(postId: number) {
  return request.delete<unknown, void>(`/circle/post/${postId}`)
}

/** 置顶/取消置顶 */
export function pinPost(postId: number, pinned: boolean) {
  return request.post<unknown, void>(`/circle/post/${postId}/${pinned ? 'pin' : 'unpin'}`)
}

/** 点赞/取消点赞 */
export function toggleLike(postId: number) {
  return request.post<unknown, void>(`/circle/post/${postId}/like`)
}

// ===== 评论 =====

/** 评论列表（楼中楼，匿名可看） */
export function getComments(postId: number) {
  return request.get<unknown, CommentVO[]>(`/circle/post/${postId}/comments`)
}

/** 评论/回复评论 */
export function createComment(postId: number, data: { content: string; parentId?: number }) {
  return request.post<unknown, number>(`/circle/post/${postId}/comment`, data)
}

/** 删评论 */
export function deleteComment(commentId: number) {
  return request.delete<unknown, void>(`/circle/comment/${commentId}`)
}

// ===== 一起听 =====

/** 发起一起听（圈主/管理员） */
export function createListenRoom(circleId: number, data: { theme?: string; songId?: number }) {
  return request.post<unknown, ListenRoomVO>(`/circle/${circleId}/listen`, null, {
    params: { theme: data.theme, songId: data.songId },
  })
}

/** 圈子当前进行中的房间 */
export function getLiveRoom(circleId: number) {
  return request.get<unknown, ListenRoomVO | null>(`/circle/${circleId}/listen/live`)
}

/** 全站进行中的一起听（广场） */
export function getLiveRooms(limit = 20) {
  return request.get<unknown, ListenRoomVO[]>('/circle/listen/live', { params: { limit } })
}

/** 结束一起听 */
export function endListenRoom(roomId: number) {
  return request.post<unknown, void>(`/circle/listen/${roomId}/end`)
}

/** 禁言/解除禁言 */
export function muteListenMember(roomId: number, targetUserId: number, muted: boolean) {
  return request.post<unknown, void>(`/circle/listen/${roomId}/mute`, null, {
    params: { targetUserId, muted },
  })
}

/** 踢出成员 */
export function kickListenMember(roomId: number, targetUserId: number) {
  return request.delete<unknown, void>(`/circle/listen/${roomId}/member/${targetUserId}`)
}

/**
 * 一起听 WebSocket 连接地址（走网关 → circle 服务）
 * 浏览器 WS 无法带 Authorization 头，token 走 URL 参数（后端握手拦截器校验）
 */
export function listenWsUrl(roomId: number): string {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  const token = localStorage.getItem('token') || ''
  return `${proto}://${location.host}/api/circle/ws/listen/${roomId}?token=${encodeURIComponent(token)}`
}
