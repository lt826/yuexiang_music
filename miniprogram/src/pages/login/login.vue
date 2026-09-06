<script setup lang="ts">
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import { register } from '@/api/user'

const userStore = useUserStore()
const playerStore = usePlayerStore()

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const email = ref('')
const submitting = ref(false)

async function submit() {
  if (submitting.value) return
  if (!username.value.trim() || !password.value) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  if (mode.value === 'register' && !email.value.trim()) {
    uni.showToast({ title: '请输入邮箱', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    if (mode.value === 'login') {
      await userStore.login(username.value.trim(), password.value)
      uni.showToast({ title: '登录成功', icon: 'success' })
    } else {
      await register({
        username: username.value.trim(),
        password: password.value,
        email: email.value.trim(),
      })
      uni.showToast({ title: '注册成功，自动登录', icon: 'success' })
      await userStore.login(username.value.trim(), password.value)
    }
    setTimeout(() => {
      uni.switchTab({ url: '/pages/index/index' })
    }, 400)
  } catch {
    // request 层已 toast
  } finally {
    submitting.value = false
  }
}

function logoutAndClear() {
  playerStore.stop()
  userStore.logout()
}
</script>

<template>
  <view class="login-page">
    <view class="brand">
      <view class="brand-mark">♪</view>
      <view class="brand-name">悦享音乐</view>
    </view>

    <view class="form card">
      <view class="tabs">
        <view class="tab" :class="{ 'is-active': mode === 'login' }" @tap="mode = 'login'"
          >登录</view
        >
        <view class="tab" :class="{ 'is-active': mode === 'register' }" @tap="mode = 'register'"
          >注册</view
        >
      </view>

      <view class="field">
        <input v-model="username" class="input" placeholder="用户名" placeholder-class="ph" />
      </view>
      <view v-if="mode === 'register'" class="field">
        <input v-model="email" class="input" placeholder="邮箱" placeholder-class="ph" />
      </view>
      <view class="field">
        <input
          v-model="password"
          class="input"
          placeholder="密码"
          password
          placeholder-class="ph"
          @confirm="submit"
        />
      </view>

      <button class="btn-primary submit" :disabled="submitting" @tap="submit">
        {{ submitting ? '提交中...' : mode === 'login' ? '登录' : '注册并登录' }}
      </button>
      <view v-if="userStore.isLoggedIn" class="logged-hint" @tap="logoutAndClear">
        当前已登录：{{ userStore.nickname }}，点此退出
      </view>
    </view>

    <view class="footer-hint">与 Web 端共用同一账号体系</view>
  </view>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  padding: 100rpx 40rpx 40rpx;
  background: linear-gradient(180deg, #2a78d6 0%, #eef4fd 45%, #f5f6fa 100%);
  box-sizing: border-box;
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 70rpx;
}

.brand-mark {
  width: 120rpx;
  height: 120rpx;
  border-radius: 36rpx;
  background: #fff;
  color: #2a78d6;
  font-size: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10rpx 40rpx rgba(28, 92, 171, 0.3);
}

.brand-name {
  color: #fff;
  font-size: 44rpx;
  font-weight: 700;
  letter-spacing: 4rpx;
}

.form {
  padding: 40rpx 36rpx;
}

.tabs {
  display: flex;
  gap: 48rpx;
  margin-bottom: 36rpx;
}

.tab {
  font-size: 32rpx;
  color: #9aa0ab;
  padding-bottom: 10rpx;
}

.tab.is-active {
  color: #22242a;
  font-weight: 600;
  border-bottom: 6rpx solid #2a78d6;
}

.field {
  margin-bottom: 28rpx;
}

.input {
  height: 92rpx;
  background: #f5f6fa;
  border-radius: 16rpx;
  padding: 0 28rpx;
  font-size: 30rpx;
}

.ph {
  color: #b6bcc7;
}

.submit {
  margin-top: 16rpx;
}

.logged-hint {
  margin-top: 24rpx;
  text-align: center;
  font-size: 24rpx;
  color: #2a78d6;
}

.footer-hint {
  margin-top: 40rpx;
  text-align: center;
  font-size: 24rpx;
  color: #9aa0ab;
}
</style>
