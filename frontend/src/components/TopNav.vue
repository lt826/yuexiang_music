<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import { getUnreadCount, getMessages, markAllRead, type MessageVO } from '@/api/message'

const router = useRouter()
const userStore = useUserStore()
const playerStore = usePlayerStore()

function handleCommand(command: string) {
  if (command === 'logout') {
    userStore.logout()
    playerStore.stop()
    ElMessage.success('已退出登录')
    router.push('/login')
  } else if (command === 'my-music') {
    router.push('/my-music')
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

function roleLabel(role: string): string {
  if (role === 'ADMIN') return '管理员'
  if (role === 'SINGER') return '歌手'
  return '普通用户'
}

// ===== 消息铃铛 =====
const unread = ref(0)
const messageVisible = ref(false)
const messages = ref<MessageVO[]>([])
const messageLoading = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

async function refreshUnread() {
  if (!userStore.isLoggedIn) return
  try {
    unread.value = await getUnreadCount()
  } catch {
    // 静默失败，不打扰用户
  }
}

async function openMessages() {
  messageVisible.value = true
  messageLoading.value = true
  try {
    const page = await getMessages(1, 10)
    messages.value = page.items
    // 打开面板即视为已读：红点即时消失，无需逐条点击
    if (page.unread > 0) {
      unread.value = 0
      markAllRead().catch(() => undefined)
    }
  } catch {
    // 拦截器已提示
  } finally {
    messageLoading.value = false
  }
}

function formatTime(time: string): string {
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  refreshUnread()
  pollTimer = setInterval(refreshUnread, 60000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <header class="top-nav">
    <div class="nav-inner">
      <router-link to="/home" class="logo">
        <span class="logo-mark">♪</span>
        <span class="logo-name">悦享音乐</span>
      </router-link>

      <nav class="menu">
        <router-link to="/home" class="menu-item" exact-active-class="is-active">首页</router-link>
        <router-link to="/search" class="menu-item" exact-active-class="is-active">搜索</router-link>
        <!-- AI 智能找歌入口 -->
        <router-link to="/ai-search" class="menu-item ai-item" exact-active-class="is-active">AI 找歌</router-link>
        <!-- 心情电台入口：与 AI 找歌同级的特色入口 -->
        <router-link to="/radio" class="menu-item radio-item" exact-active-class="is-active">
          <span class="radio-dot" />心情电台
        </router-link>
        <!-- 乐坊（圈子社区）：广场/详情匿名可逛 -->
        <router-link to="/circle" class="menu-item" exact-active-class="is-active">乐坊</router-link>
        <router-link to="/playlists" class="menu-item" exact-active-class="is-active">歌单</router-link>
        <router-link to="/my-music" class="menu-item" exact-active-class="is-active">我的音乐</router-link>
        <!-- 歌手中心：歌手角色显示工作台，普通用户显示申请入口 -->
        <router-link to="/singer-center" class="menu-item" exact-active-class="is-active">
          {{ userStore.role === 'SINGER' ? '歌手中心' : '成为歌手' }}
        </router-link>
      </nav>

      <div class="nav-right">
        <template v-if="userStore.isLoggedIn">
          <!-- 消息铃铛 -->
          <el-popover
            v-model:visible="messageVisible"
            width="340"
            trigger="click"
            @show="openMessages"
          >
            <template #reference>
              <el-badge :value="unread" :hidden="unread === 0" :max="99" class="bell-badge">
                <el-icon :size="18" class="bell"><Bell /></el-icon>
              </el-badge>
            </template>
            <div class="msg-panel">
              <div class="msg-head">
                <span>消息通知</span>
                <span class="msg-hint">打开即全部已读</span>
              </div>
              <div v-loading="messageLoading" class="msg-list">
                <p v-if="!messageLoading && messages.length === 0" class="msg-empty">暂无消息</p>
                <div
                  v-for="msg in messages"
                  :key="msg.id"
                  class="msg-item"
                >
                  <div class="msg-item-head">
                    <el-tag
                      :type="msg.type === 'AUDIT' ? 'danger' : 'info'"
                      size="small"
                      effect="plain"
                    >
                      {{ msg.type === 'AUDIT' ? '审核' : '系统' }}
                    </el-tag>
                    <span class="msg-time">{{ formatTime(msg.createTime) }}</span>
                  </div>
                  <p class="msg-title">{{ msg.title }}</p>
                  <p class="msg-content">{{ msg.content }}</p>
                </div>
              </div>
            </div>
          </el-popover>

          <el-dropdown @command="handleCommand">
            <div class="user-entry">
              <el-avatar :size="30" :src="userStore.avatar || undefined" class="user-avatar">
                {{ userStore.nickname.slice(0, 1) }}
              </el-avatar>
              <el-tooltip :content="userStore.nickname" placement="bottom" :show-after="300">
                <span class="user-name">{{ userStore.nickname }}</span>
              </el-tooltip>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  {{ roleLabel(userStore.role) }}
                </el-dropdown-item>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="my-music">我的音乐</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <router-link to="/login" class="login-link">登录</router-link>
          <router-link to="/register" class="register-link">免费注册</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
/* ===== 顶栏：整条品牌蓝，高度对齐主内容 ===== */
.top-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--yx-gradient);
  box-shadow: 0 2px 12px rgba(28, 92, 171, 0.25);
}

/* 与页面主区同宽（1160px），保证导航和内容左边缘对齐 */
.nav-inner {
  max-width: 1160px;
  margin: 0 auto;
  height: 60px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 28px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #fff;
  flex-shrink: 0;
}

.logo-mark {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  color: #1c5cab;
  border-radius: 50%;
  background: #fff;
}

.logo-name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
}

/* 导航项：统一用 flex 居中，全部等高对齐；不换行防止挤压时文字竖排 */
.menu {
  display: flex;
  align-items: stretch;
  align-self: stretch;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 0 12px;
  font-size: 14px;
  /* 中文菜单字距：4px 够气质也不虚胖（曾 12px，加入电台入口后 1440px 宽度溢出挤压右侧登录区） */
  letter-spacing: 4px;
  text-indent: 4px;
  white-space: nowrap;
  color: rgba(255, 255, 255, 0.82);
  cursor: pointer;
  border-radius: 999px;
  transition: background 0.15s ease, color 0.15s ease;
}

.menu-item:hover {
  color: #fff;
  background: rgba(0, 0, 0, 0.14);
}

/* 当前页：白底蓝字胶囊（顶栏选中态） */
.menu-item.is-active {
  color: #1c5cab;
  background: #fff;
  font-weight: 600;
}

/* AI 入口：琥珀色文字微区分（特色入口，不用底色块） */
.menu-item.ai-item {
  color: #ffd591;
  font-weight: 600;
}

.menu-item.ai-item:hover {
  color: #fff;
  background: rgba(0, 0, 0, 0.14);
}

.menu-item.ai-item.is-active {
  background: #fff;
  color: #1c5cab;
}

/* 心情电台入口：琥珀信号灯 + 常规底色（特色靠小灯，不靠色块） */
.menu-item.radio-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.radio-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #f0b34e;
  box-shadow: 0 0 6px rgba(240, 179, 78, 0.8);
  flex-shrink: 0;
}

.menu-item.radio-item:hover {
  background: rgba(0, 0, 0, 0.14);
  color: #fff;
}

.menu-item.radio-item.is-active {
  background: #fff;
  color: #1c5cab;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 1;
  min-width: 0;
}

/* 消息铃铛 */
.bell-badge {
  display: flex;
  align-items: center;
}

.bell {
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
}

.bell:hover {
  color: #fff;
}

.login-link {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.88);
}

.login-link:hover {
  color: #fff;
}

.register-link {
  font-size: 14px;
  color: #1c5cab;
  padding: 6px 16px;
  border-radius: 999px;
  background: #fff;
  font-weight: 600;
  transition: transform 0.15s ease;
}

.register-link:hover {
  transform: scale(1.03);
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #fff;
  min-width: 0;
}

.user-avatar {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.4);
  flex-shrink: 0;
}

/* 昵称定宽截断：无论多长都不影响导航栏整体布局，悬停 tooltip 显示全名 */
.user-name {
  font-size: 14px;
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 消息面板 */
.msg-panel {
  margin: -12px;
}

.msg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  font-size: 14px;
  font-weight: 600;
  border-bottom: 1px solid var(--yx-hairline);
}

.msg-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--yx-text-disabled);
}

.msg-list {
  max-height: 360px;
  overflow-y: auto;
  padding: 6px 0;
}

.msg-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--yx-text-secondary);
}

.msg-item {
  padding: 10px 14px;
  border-bottom: 1px solid var(--yx-hairline);
}

.msg-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.msg-time {
  font-size: 11px;
  color: var(--yx-text-disabled);
}

.msg-title {
  font-size: 13px;
  margin-bottom: 2px;
}

.msg-content {
  font-size: 12px;
  color: var(--yx-text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 960px) {
  .menu {
    display: none;
  }
}
</style>
