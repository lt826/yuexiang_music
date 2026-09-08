import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi } from '@/api/user'
import type { LoginVO } from '@/api/types'

const TOKEN_KEY = 'yx_token'
const USER_KEY = 'yx_user'

interface StoredUser {
  userId: number
  nickname: string
  avatar: string
  role: string
}

function readStorage(): { token: string; user: StoredUser | null } {
  const token = (uni.getStorageSync(TOKEN_KEY) as string) || ''
  let user: StoredUser | null = null
  try {
    const raw = uni.getStorageSync(USER_KEY) as string
    if (raw) user = JSON.parse(raw) as StoredUser
  } catch {
    user = null
  }
  return { token, user }
}

/** 用户登录态（对齐 Web 端 userStore；storage 换成 uni.setStorageSync） */
export const useUserStore = defineStore('user', () => {
  const init = readStorage()
  const token = ref(init.token)
  const user = ref<StoredUser | null>(init.user)

  const isLoggedIn = computed(() => !!token.value)
  const nickname = computed(() => user.value?.nickname || '')
  const avatar = computed(() => user.value?.avatar || '')
  const role = computed(() => user.value?.role || '')

  async function login(username: string, password: string) {
    const vo: LoginVO = await loginApi({ username, password })
    token.value = vo.token
    user.value = {
      userId: vo.userId,
      nickname: vo.nickname || vo.username,
      avatar: vo.avatar || '',
      role: vo.role,
    }
    uni.setStorageSync(TOKEN_KEY, vo.token)
    uni.setStorageSync(USER_KEY, JSON.stringify(user.value))
  }

  function logout() {
    token.value = ''
    user.value = null
    uni.removeStorageSync(TOKEN_KEY)
    uni.removeStorageSync(USER_KEY)
  }

  return { token, user, isLoggedIn, nickname, avatar, role, login, logout }
})
