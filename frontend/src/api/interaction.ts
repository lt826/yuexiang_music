import request from './request'

/** 评论条目（含评论人昵称/头像，后端已聚合） */
export interface CommentVO {
  id: number
  songId: number
  userId: number
  /** 昵称（user 服务故障降级为"用户{id}"） */
  nickname: string
  /** 头像（可能为 null，用昵称首字兜底） */
  avatar: string | null
  content: string
  /** 根评论ID（null=根评论） */
  rootId: number | null
  /** 被回复的用户ID（仅回复条目有值） */
  replyUserId: number | null
  /** 被回复人昵称（仅回复条目下发） */
  replyNickname?: string | null
  /** 点赞数 */
  likeCount: number
  /** 当前用户是否已赞 */
  liked: boolean
  /** 回复数（仅根评论下发） */
  replyCount: number
  /** 回复预览（仅根评论下发，最新 2 条） */
  replyPreview?: CommentVO[] | null
  /** 格式化时间 "yyyy-MM-dd HH:mm" */
  time: string
}

/** 评论分页 */
export interface CommentPageVO {
  items: CommentVO[]
  total: number
  page: number
  size: number
}

/** 回复分页（含根评论本体，展开楼中楼时顶栏展示） */
export interface ReplyPageVO {
  items: CommentVO[]
  total: number
  root: CommentVO
}

/** 歌曲互动聚合（播放页顶栏展示） */
export interface SongInteractionVO {
  likeCount: number
  liked: boolean
  commentCount: number
}

/** 评论排序：recommend 推荐 / hot 最热 / time 最新 */
export type CommentSort = 'recommend' | 'hot' | 'time'

/** 查询互动聚合（点赞数/是否已赞/评论数），匿名可看 */
export function getInteraction(songId: number) {
  return request.get<unknown, SongInteractionVO>(`/song/${songId}/interaction`)
}

/** 点赞 → 返回最新总数 */
export function likeSong(songId: number) {
  return request.post<unknown, number>(`/song/${songId}/like`)
}

/** 取消点赞 → 返回最新总数 */
export function unlikeSong(songId: number) {
  return request.delete<unknown, number>(`/song/${songId}/like`)
}

/** 根评论分页（sort: recommend/hot/time），匿名可看 */
export function getComments(songId: number, page = 1, size = 20, sort: CommentSort = 'time') {
  return request.get<unknown, CommentPageVO>(`/song/${songId}/comments`, { params: { page, size, sort } })
}

/** 某根评论下的回复分页（时间正序），匿名可看 */
export function getReplies(commentId: number, page = 1, size = 20) {
  return request.get<unknown, ReplyPageVO>(`/song/comment/${commentId}/replies`, { params: { page, size } })
}

/**
 * 发表评论/回复 → 返回完整条目（直接插入列表）
 * @param rootId 根评论ID（回复时必传）；replyToId 指定"回复谁"（楼中楼内回复楼中楼层）
 */
export function postComment(songId: number, content: string, rootId?: number, replyToId?: number) {
  return request.post<unknown, CommentVO>(`/song/${songId}/comment`, {
    content,
    rootId,
    replyToId,
  })
}

/** 删除自己的评论 */
export function deleteComment(commentId: number) {
  return request.delete<unknown, void>(`/song/comment/${commentId}`)
}

/** 评论点赞 → 返回最新点赞数 */
export function likeComment(commentId: number) {
  return request.post<unknown, number>(`/song/comment/${commentId}/like`)
}

/** 取消评论点赞 → 返回最新点赞数 */
export function unlikeComment(commentId: number) {
  return request.delete<unknown, number>(`/song/comment/${commentId}/like`)
}
