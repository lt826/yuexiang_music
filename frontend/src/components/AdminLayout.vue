<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DataAnalysis,
  Headset,
  Setting,
  SwitchButton,
  Fold,
  Expand,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { usePlayerStore } from '@/stores/player'
import { pendingStore } from '@/stores/pending'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const playerStore = usePlayerStore()

/** 侧边栏折叠态（宽屏也可收起，给内容区更大空间） */
const collapsed = ref(false)

const activeMenu = computed(() => String(route.name || 'admin-home'))

function logout() {
  userStore.logout()
  playerStore.stop()
  router.push('/admin/login')
}

const menu = computed(() => [
  { name: 'admin-home', index: '/admin/home', label: '首页', icon: Headset },
  { name: 'admin-manage', index: '/admin/manage', label: '管理中心', icon: DataAnalysis, badge: pendingStore.total.value },
  { name: 'admin-setting', index: '/admin/setting', label: '基本设置', icon: Setting },
])

const pageTitle = computed(() => {
  const found = menu.value.find((m) => m.name === activeMenu.value)
  return found?.label ?? '首页'
})

function handleSelect(index: string) {
  router.push(index)
}
</script>

<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ 'is-collapsed': collapsed }">
      <div class="brand">
        <span class="brand-mark">♪</span>
        <transition name="fade">
          <span v-if="!collapsed" class="brand-name">悦享后台</span>
        </transition>
      </div>

      <el-menu
        class="side-menu"
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="transparent"
        text-color="#a3abbd"
        active-text-color="#ffffff"
        @select="handleSelect"
      >
        <el-menu-item v-for="m in menu" :key="m.name" :index="m.index">
          <el-icon><component :is="m.icon" /></el-icon>
          <template #title>
            <span class="menu-label">{{ m.label }}</span>
            <span v-if="m.badge" class="menu-badge">{{ m.badge > 99 ? '99+' : m.badge }}</span>
          </template>
        </el-menu-item>
      </el-menu>

      <div class="side-foot">
        <div class="side-user" v-if="!collapsed">
          <el-avatar :size="34" class="side-avatar">{{ userStore.nickname.slice(0, 1) }}</el-avatar>
          <div class="side-user-info">
            <span class="side-user-name">{{ userStore.nickname }}</span>
            <span class="side-user-role">管理员</span>
          </div>
        </div>
        <div class="side-actions">
          <el-tooltip :content="collapsed ? '展开菜单' : '收起菜单'" placement="top">
            <el-icon class="side-action" :size="16" @click="collapsed = !collapsed">
              <Expand v-if="collapsed" />
              <Fold v-else />
            </el-icon>
          </el-tooltip>
          <el-tooltip v-if="!collapsed" content="退出登录" placement="top">
            <el-icon class="side-action" :size="16" @click="logout"><SwitchButton /></el-icon>
          </el-tooltip>
        </div>
      </div>
    </aside>

    <!-- 右侧：顶栏 + 内容 -->
    <div class="admin-body">
      <header class="admin-topbar">
        <h1 class="page-title">{{ pageTitle }}</h1>
        <div class="topbar-right">
          <el-tooltip :content="userStore.nickname" placement="bottom" :show-after="300">
            <span class="greet">欢迎回来，{{ userStore.nickname }}</span>
          </el-tooltip>
          <el-button text size="small" type="danger" @click="logout">退出</el-button>
        </div>
      </header>

      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
  background: var(--yx-page);
}

/* ===== 侧边栏：深海军蓝，白字对比 15.49:1 ===== */
.sidebar {
  width: 224px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #1e2433 0%, #171c28 100%);
  color: #fff;
  transition: width 0.2s ease;
  overflow: hidden;
}

.sidebar.is-collapsed {
  width: 64px;
}

.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  flex-shrink: 0;
}

.sidebar.is-collapsed .brand {
  justify-content: center;
  padding: 0;
}

.brand-mark {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 18px;
  color: #fff;
  background: var(--yx-gradient);
  flex-shrink: 0;
}

.brand-name {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
  white-space: nowrap;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 菜单 */
.side-menu {
  flex: 1;
  border-right: none;
  padding: 14px 10px;
  overflow-y: auto;
}

.side-menu :deep(.el-menu-item) {
  height: 46px;
  line-height: 46px;
  margin-bottom: 6px;
  border-radius: 8px;
  font-size: 14.5px;
}

.side-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.07);
}

.side-menu :deep(.el-menu-item.is-active) {
  background: var(--yx-gradient);
  box-shadow: 0 4px 12px rgba(28, 92, 171, 0.4);
}

.menu-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* 待办角标 */
.menu-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #d03b3b;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  font-weight: 600;
}

/* 底部用户区 */
.side-foot {
  padding: 14px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
  flex-shrink: 0;
}

.side-user {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.side-avatar {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  flex-shrink: 0;
}

.side-user-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.side-user-name {
  font-size: 13.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-user-role {
  font-size: 11.5px;
  color: #7f8aa3;
  margin-top: 2px;
}

.side-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.sidebar.is-collapsed .side-actions {
  justify-content: center;
}

.side-action {
  color: #7f8aa3;
  cursor: pointer;
}

.side-action:hover {
  color: #fff;
}

/* ===== 右侧主体 ===== */
.admin-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow-y: auto;
}

.admin-topbar {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: #fff;
  border-bottom: 1px solid var(--yx-hairline);
  position: sticky;
  top: 0;
  z-index: 10;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--yx-text-main);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex-shrink: 1;
}

/* 欢迎语窄屏/长昵称下截断，悬停 tooltip 显示全名 */
.greet {
  font-size: 13px;
  color: var(--yx-text-secondary);
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 内容区：全宽、左对齐、不再居中限宽 */
.admin-content {
  flex: 1;
  padding: 24px 28px 40px;
  min-width: 0;
}
</style>
