import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, type LoginVO } from '@/api/user'
import type { AdminLoginVO } from '@/api/admin'

/** 用户登录态（对齐详细设计 13.4：userStore 管登录态、角色、令牌） */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userId = ref<number>(Number(localStorage.getItem('userId')) || 0)
  const nickname = ref<string>(localStorage.getItem('nickname') || '')
  const avatar = ref<string>(localStorage.getItem('avatar') || '')
  const role = ref<string>(localStorage.getItem('role') || '')

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')

  async function login(username: string, password: string) {
    const vo: LoginVO = await loginApi({ username, password })
    token.value = vo.token
    userId.value = vo.userId
    nickname.value = vo.nickname || vo.username
    avatar.value = vo.avatar || ''
    role.value = vo.role
    localStorage.setItem('token', vo.token)
    localStorage.setItem('userId', String(vo.userId))
    localStorage.setItem('nickname', vo.nickname || vo.username)
    localStorage.setItem('avatar', vo.avatar || '')
    localStorage.setItem('role', vo.role)
  }

  /** 管理员登录（后台管理独立链路） */
  function setAdmin(vo: AdminLoginVO) {
    token.value = vo.token
    userId.value = vo.adminId
    nickname.value = vo.nickname || vo.username
    avatar.value = ''
    role.value = 'ADMIN'
    localStorage.setItem('token', vo.token)
    localStorage.setItem('userId', String(vo.adminId))
    localStorage.setItem('nickname', vo.nickname || vo.username)
    localStorage.setItem('avatar', '')
    localStorage.setItem('role', 'ADMIN')
  }

  function logout() {
    token.value = ''
    userId.value = 0
    nickname.value = ''
    avatar.value = ''
    role.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('nickname')
    localStorage.removeItem('avatar')
    localStorage.removeItem('role')
  }

  return { token, userId, nickname, avatar, role, isLoggedIn, isAdmin, login, setAdmin, logout }
})
