import request from './request'

/** 登录返回（对齐详细设计 12.2：token、userId、nickname、avatar、role） */
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

export function login(data: { username: string; password: string }) {
  return request.post<unknown, LoginVO>('/user/login', data)
}

export function register(data: { username: string; email: string; password: string }) {
  return request.post<unknown, void>('/user/register', data)
}

export function getUserInfo() {
  return request.get<unknown, UserInfo>('/user/info')
}

/** 用户公开主页信息（匿名可看，仅昵称/头像/签名/角色/注册时间，不含用户名与手机邮箱） */
export interface UserPublicInfo {
  id: number
  nickname: string | null
  avatar: string | null
  signature: string | null
  role: string
  createTime: string
}

/** 查询他人公开主页（帖子/评论作者点击进入） */
export function getUserPublicInfo(id: number) {
  return request.get<unknown, UserPublicInfo>(`/user/${id}`)
}

// ===== 用户互关（关注/粉丝） =====

/** 关注/粉丝计数汇总（匿名可看；登录时附带 following 关注态） */
export interface FollowSummaryVO {
  userId: number
  followingCount: number
  followerCount: number
  /** 查看者是否已关注该用户（匿名时为 null） */
  following: boolean | null
}

/** 关注/粉丝列表条目（点击进对方公开主页） */
export interface FollowUserVO {
  id: number
  nickname: string | null
  avatar: string | null
  signature: string | null
  /** 查看者是否已关注此人（匿名时为 null） */
  followed: boolean | null
  /** 被关注者角色（USER/SINGER/ADMIN）：SINGER 统一显示"歌手"标签 */
  role: string
}

/** 关注（登录；幂等，重复关注返回 false） */
export function followUser(id: number) {
  return request.post<unknown, boolean>(`/user/${id}/follow`)
}

/** 取消关注（登录；幂等） */
export function unfollowUser(id: number) {
  return request.delete<unknown, void>(`/user/${id}/follow`)
}

/** 关注/粉丝计数 */
export function getFollowSummary(id: number) {
  return request.get<unknown, FollowSummaryVO>(`/user/${id}/follow-summary`)
}

/** 某用户的关注列表 */
export function getFollowingList(id: number) {
  return request.get<unknown, FollowUserVO[]>(`/user/${id}/following`)
}

/** 某用户的粉丝列表 */
export function getFollowersList(id: number) {
  return request.get<unknown, FollowUserVO[]>(`/user/${id}/followers`)
}

/** 修改昵称与签名（个人中心），返回更新后的用户信息 */
export function updateProfile(data: { nickname?: string; signature?: string }) {
  return request.put<unknown, UserInfo>('/user/profile', data)
}

/** 修改头像（URL 由 file 服务上传返回），返回更新后的用户信息 */
export function updateAvatar(avatar: string) {
  return request.put<unknown, UserInfo>('/user/avatar', { avatar })
}

/** 修改密码（校验旧密码，成功后需重新登录） */
export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request.post<unknown, void>('/user/password', data)
}
