<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getUserInfo, updateProfile, changePassword } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import type { UserInfo } from '@/api/types'

const userStore = useUserStore()
const playerStore = usePlayerStore()

const info = ref<UserInfo | null>(null)
const nickname = ref('')
const signature = ref('')
const saving = ref(false)

const showPwd = ref(false)
const oldPwd = ref('')
const newPwd = ref('')

async function load() {
  if (!userStore.isLoggedIn) return
  try {
    info.value = await getUserInfo()
    nickname.value = info.value.nickname || ''
    signature.value = info.value.signature || ''
  } catch {
    // 已提示
  }
}

async function save() {
  if (saving.value) return
  saving.value = true
  try {
    info.value = await updateProfile({
      nickname: nickname.value.trim() || undefined,
      signature: signature.value,
    })
    // 同步本地登录态昵称
    if (userStore.user) {
      userStore.user.nickname = info.value.nickname || info.value.username
      uni.setStorageSync('yx_user', JSON.stringify(userStore.user))
    }
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch {
    // 已提示
  } finally {
    saving.value = false
  }
}

async function submitPwd() {
  if (!oldPwd.value || !newPwd.value) {
    uni.showToast({ title: '请填写旧密码与新密码', icon: 'none' })
    return
  }
  try {
    await changePassword({ oldPassword: oldPwd.value, newPassword: newPwd.value })
    uni.showToast({ title: '密码已修改，请重新登录', icon: 'none' })
    setTimeout(() => {
      playerStore.stop()
      userStore.logout()
      uni.reLaunch({ url: '/pages/login/login' })
    }, 900)
  } catch {
    // 已提示
  }
}

function logout() {
  uni.showModal({
    title: '退出登录',
    content: '确定退出当前账号吗？',
    success: (res) => {
      if (!res.confirm) return
      playerStore.stop()
      userStore.logout()
      uni.reLaunch({ url: '/pages/login/login' })
    },
  })
}

onShow(load)
</script>

<template>
  <view class="page">
    <view v-if="info" class="card">
      <view class="row">
        <view class="label">用户名</view>
        <view class="value">{{ info.username }}</view>
      </view>
      <view class="row">
        <view class="label">邮箱</view>
        <view class="value">{{ info.email || '未填写' }}</view>
      </view>
      <view class="row">
        <view class="label">昵称</view>
        <input
          v-model="nickname"
          class="value input-value"
          :maxlength="30"
          placeholder="输入新昵称"
        />
      </view>
      <!-- 签名独立成块：避免与下方按钮互相挤压/原生组件错位遮挡 -->
      <view class="sig-block">
        <view class="sig-label">个性签名</view>
        <textarea
          v-model="signature"
          class="sig-textarea"
          :maxlength="100"
          placeholder="写一句介绍自己"
          :adjust-position="false"
          :show-confirm-bar="false"
          cursor-spacing="20"
        />
      </view>
      <button class="btn-primary save-btn" :disabled="saving" @tap="save">
        {{ saving ? '保存中...' : '保存资料' }}
      </button>
    </view>

    <view class="card">
      <view class="card-title" @tap="showPwd = !showPwd">
        修改密码
        <text class="fold">{{ showPwd ? '收起' : '展开' }}</text>
      </view>
      <template v-if="showPwd">
        <view class="pwd-field">
          <input v-model="oldPwd" class="pwd-input" password placeholder="旧密码" />
        </view>
        <view class="pwd-field">
          <input v-model="newPwd" class="pwd-input" password placeholder="新密码" />
        </view>
        <button class="btn-primary save-btn" @tap="submitPwd">确认修改</button>
      </template>
    </view>

    <view class="logout-btn" @tap="logout">退出登录</view>

    <view class="bottom-space" />
  </view>
</template>

<style scoped>
.page {
  padding: 20rpx 0 0;
}

.row {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f2f6;
}

.row.col {
  flex-direction: column;
  align-items: stretch;
  gap: 14rpx;
}

.sig-block {
  padding: 20rpx 0;
}

.sig-label {
  font-size: 28rpx;
  color: #66708a;
  margin-bottom: 14rpx;
}

/* relative 定位 + 层级，防止原生 textarea 渲染层盖住页面元素 */
.sig-textarea {
  position: relative;
  z-index: 1;
  background: #f5f6fa;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  height: 140rpx;
  width: 100%;
  box-sizing: border-box;
  font-size: 26rpx;
}

.label {
  width: 160rpx;
  font-size: 28rpx;
  color: #66708a;
  flex-shrink: 0;
}

.value {
  flex: 1;
  font-size: 28rpx;
}

.input-value {
  background: #f5f6fa;
  border-radius: 12rpx;
  height: 72rpx;
  padding: 0 20rpx;
}

.textarea-value {
  background: #f5f6fa;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  height: 140rpx;
  width: 100%;
  box-sizing: border-box;
  font-size: 26rpx;
}

.save-btn {
  margin-top: 30rpx;
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.fold {
  font-size: 24rpx;
  color: #2a78d6;
  font-weight: 400;
}

.pwd-field {
  margin-top: 24rpx;
}

.pwd-input {
  height: 84rpx;
  background: #f5f6fa;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
}

.logout-btn {
  margin: 40rpx 24rpx 0;
  height: 92rpx;
  line-height: 92rpx;
  text-align: center;
  background: #fff;
  border-radius: 20rpx;
  color: #d03b3b;
  font-size: 30rpx;
}

/* 给底部迷你播放条留出空间，避免按钮被遮挡无法点击 */
.bottom-space {
  height: 220rpx;
}
</style>
