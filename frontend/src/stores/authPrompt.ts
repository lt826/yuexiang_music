import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 登录拦截弹窗（未登录点播放时弹出）
 * 携带 pendingAction：登录成功后自动执行（恢复被拦截的播放操作）
 */
export const useAuthPromptStore = defineStore('authPrompt', () => {
  const visible = ref(false)
  /** 登录成功后要恢复的动作（如重新播放某首歌） */
  const pendingAction = ref<(() => void) | null>(null)

  function open(action?: () => void) {
    pendingAction.value = action ?? null
    visible.value = true
  }

  function close() {
    visible.value = false
    pendingAction.value = null
  }

  /** 登录成功：关弹窗并恢复挂起的操作 */
  function resolve() {
    const action = pendingAction.value
    close()
    action?.()
  }

  return { visible, pendingAction, open, close, resolve }
})
