<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useAuthPromptStore } from '@/stores/authPrompt'
import { usePlayerStore } from '@/stores/player'

/** 全局登录拦截弹窗：未登录点播放时弹出（白卡 16px 圆角 + 半透明黑遮罩，点遮罩关闭） */
const router = useRouter()
const userStore = useUserStore()
const authPrompt = useAuthPromptStore()
const playerStore = usePlayerStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    ElMessage.success('登录成功，继续播放')
    // 登录成功：弹窗消失并恢复挂起的播放操作
    authPrompt.resolve()
    form.password = ''
  } finally {
    loading.value = false
  }
}

/** 点遮罩关闭（不恢复播放） */
function onMaskClick(e: MouseEvent) {
  if (e.target === e.currentTarget) {
    authPrompt.close()
  }
}
</script>

<template>
  <teleport to="body">
    <transition name="mask-fade">
      <div v-if="authPrompt.visible" class="login-mask" @click="onMaskClick">
        <transition name="card-pop" appear>
          <div class="login-card" @click.stop>
            <button class="card-close" title="关闭" @click="authPrompt.close">✕</button>
            <div class="card-head">
              <span class="card-mark">♪</span>
              <h2 class="card-title">登录悦享音乐</h2>
              <p class="card-sub">登录后即可播放、收藏与管理你的音乐</p>
            </div>
            <el-form
              ref="formRef"
              :model="form"
              :rules="rules"
              label-position="top"
              size="large"
              @keyup.enter="handleLogin"
            >
              <el-form-item prop="username">
                <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" clearable />
              </el-form-item>
              <el-form-item prop="password">
                <el-input
                  v-model="form.password"
                  type="password"
                  placeholder="密码"
                  :prefix-icon="Lock"
                  show-password
                />
              </el-form-item>
              <el-button
                class="submit-btn"
                type="primary"
                size="large"
                :loading="loading"
                @click="handleLogin"
              >
                登 录
              </el-button>
            </el-form>
            <p class="card-tip">
              还没有账号？
              <a class="tip-link" @click="authPrompt.close(); router.push('/register')">免费注册</a>
              　·
              <a class="tip-link" @click="authPrompt.close(); playerStore.toggle()">先试听</a>
            </p>
          </div>
        </transition>
      </div>
    </transition>
  </teleport>
</template>

<style scoped>
/* 半透明黑遮罩 */
.login-mask {
  position: fixed;
  inset: 0;
  z-index: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(2px);
}

/* 白色登录卡：16px 圆角 */
.login-card {
  position: relative;
  width: 380px;
  max-width: calc(100vw - 48px);
  padding: 34px 36px 28px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.card-close {
  position: absolute;
  top: 14px;
  right: 16px;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #b8bcc5;
  font-size: 14px;
  cursor: pointer;
  border-radius: 50%;
  transition: background 0.15s ease, color 0.15s ease;
}

.card-close:hover {
  background: #f2f3f6;
  color: #2f3238;
}

.card-head {
  text-align: center;
  margin-bottom: 22px;
}

.card-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  font-size: 22px;
  color: #fff;
  background: var(--yx-gradient);
  margin-bottom: 12px;
}

.card-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--yx-text-main);
}

.card-sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 6px;
}

.card-tip {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

.tip-link {
  color: var(--yx-accent);
  cursor: pointer;
  font-weight: 600;
}

.tip-link:hover {
  color: var(--yx-accent-hover);
}

/* 遮罩淡入 + 卡片上浮 */
.mask-fade-enter-active,
.mask-fade-leave-active {
  transition: opacity 0.3s ease;
}

.mask-fade-enter-from,
.mask-fade-leave-to {
  opacity: 0;
}

.card-pop-enter-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.card-pop-enter-from {
  transform: translateY(16px) scale(0.96);
  opacity: 0;
}
</style>
