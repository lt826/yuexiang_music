import request from './request'

/** 消息项 */
export interface MessageVO {
  id: number
  title: string
  content: string
  /** SYSTEM系统 AUDIT审核 FAN粉丝 */
  type: string
  isRead: number
  createTime: string
}

export interface MessagePageVO {
  items: MessageVO[]
  unread: number
}

/** 我的消息（分页+未读数） */
export function getMessages(page = 1, size = 20) {
  return request.get<unknown, MessagePageVO>('/message/list', { params: { page, size } })
}

/** 未读数（导航铃铛） */
export function getUnreadCount() {
  return request.get<unknown, number>('/message/unread')
}

/** 单条已读 */
export function markRead(id: number) {
  return request.post<unknown, void>(`/message/read/${id}`)
}

/** 全部已读 */
export function markAllRead() {
  return request.post<unknown, void>('/message/read/all')
}
